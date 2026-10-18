package com.karencalulo.wishwait.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.karencalulo.wishwait.domain.model.TipEvent
import com.karencalulo.wishwait.domain.model.TipOption
import com.karencalulo.wishwait.domain.model.TipSize
import com.karencalulo.wishwait.domain.repository.TipJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Tips through Google Play Billing (Play Billing Library 8).
 * Tips are consumable one-time products, so people can tip more than once.
 * No server: a tip is recorded on the device after Google Play confirms it and it is consumed.
 */
class PlayTipJar(
    context: Context,
    private val onTipCompleted: suspend () -> Unit,
) : TipJar, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _events = MutableSharedFlow<TipEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<TipEvent> = _events.asSharedFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connectLock = Mutex()
    private val tokensLock = Mutex()
    /** Purchase tokens being consumed right now, so one tip is never counted twice. */
    private val inFlight = mutableSetOf<String>()
    @Volatile private var details: Map<TipSize, ProductDetails> = emptyMap()

    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // Reconnects on the next call.
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    override suspend fun options(): List<TipOption>? {
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                TipSize.entries.map { size ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(size.productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val found: List<ProductDetails> = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                val list = if (result.responseCode == BillingResponseCode.OK) queryResult.productDetailsList else emptyList()
                if (cont.isActive) cont.resume(list)
            }
        }
        details = found.mapNotNull { pd -> TipSize.entries.firstOrNull { it.productId == pd.productId }?.let { it to pd } }.toMap()
        return TipSize.entries
            .mapNotNull { size -> details[size]?.oneTimePurchaseOfferDetails?.formattedPrice?.let { TipOption(size, it) } }
            .ifEmpty { null }
    }

    /** Opens Google Play's payment sheet. Returns false if the tip isn't loaded or Play refused. */
    fun launch(activity: Activity, size: TipSize): Boolean {
        val product = details[size] ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach { p -> scope.launch { handle(p, announce = true) } }
            BillingResponseCode.USER_CANCELED -> _events.tryEmit(TipEvent.Cancelled)
            // An earlier tip of the same size is still waiting to be finished.
            BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { finishPending() }
            else -> _events.tryEmit(TipEvent.Failed)
        }
    }

    override suspend fun finishPending() {
        if (!connect()) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val owned: List<Purchase> = suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, list ->
                if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list else emptyList())
            }
        }
        owned.forEach { handle(it, announce = true) }
    }

    private suspend fun handle(purchase: Purchase, announce: Boolean) {
        val isTip = purchase.products.any { id -> TipSize.entries.any { it.productId == id } }
        if (!isTip) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> if (announce) _events.emit(TipEvent.Pending)
            Purchase.PurchaseState.PURCHASED -> {
                val token = purchase.purchaseToken
                if (!tokensLock.withLock { inFlight.add(token) }) return
                try {
                    // Consuming also acknowledges the purchase (unacknowledged ones are refunded after 3 days).
                    if (consume(token)) {
                        onTipCompleted()
                        if (announce) _events.emit(TipEvent.Thanks)
                    }
                } finally {
                    tokensLock.withLock { inFlight.remove(token) }
                }
            }
            else -> Unit
        }
    }

    private suspend fun consume(token: String): Boolean = suspendCancellableCoroutine { cont ->
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { result, _ ->
            if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
        }
    }
}
