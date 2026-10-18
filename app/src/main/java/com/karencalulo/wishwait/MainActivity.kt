package com.karencalulo.wishwait

import com.karencalulo.wishwait.presentation.addwish.SharedText
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karencalulo.wishwait.data.reminder.NotificationHelper
import com.karencalulo.wishwait.data.widget.WishWaitWidget
import com.karencalulo.wishwait.domain.model.UserProfile
import com.karencalulo.wishwait.presentation.common.LocalMoney
import com.karencalulo.wishwait.presentation.common.MoneyFormatter
import com.karencalulo.wishwait.presentation.navigation.WishWaitNavHost
import com.karencalulo.wishwait.presentation.theme.WishWaitTheme

class MainActivity : ComponentActivity() {

    private var pendingWishId by mutableStateOf<String?>(null)
    private var pendingShare by mutableStateOf<SharedText?>(null)
    private var pendingAdd by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as WishWaitApp).container

        setContent {
            WishWaitTheme {
                val profile by container.profileRepository.profile
                    .collectAsStateWithLifecycle<UserProfile?>(initialValue = null)
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val p = profile
                    if (p != null) {
                        val money = remember(p.currencyCode) { MoneyFormatter(p.currencyCode) }
                        CompositionLocalProvider(LocalMoney provides money) {
                            WishWaitNavHost(
                                onboarded = p.onboarded,
                                pendingWishId = pendingWishId,
                                onPendingConsumed = { pendingWishId = null },
                                pendingShare = pendingShare,
                                onShareConsumed = { pendingShare = null },
                                pendingAdd = pendingAdd,
                                onAddConsumed = { pendingAdd = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(NotificationHelper.EXTRA_OPEN_WISH_ID)?.let { pendingWishId = it }
        if (intent.getBooleanExtra(WishWaitWidget.EXTRA_OPEN_ADD, false)) pendingAdd = true
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!text.isNullOrBlank() || !subject.isNullOrBlank()) pendingShare = SharedText(text, subject)
        }
    }
}
