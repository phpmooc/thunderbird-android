package com.fsck.k9.backends

import android.content.Context
import com.fsck.k9.backend.BackendFactory
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.imap.ImapBackend
import com.fsck.k9.backend.imap.ImapPushConfigProvider
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.power.PowerManager
import com.fsck.k9.mail.ssl.TrustedSocketFactory
import com.fsck.k9.mail.store.imap.IdleRefreshManager
import com.fsck.k9.mail.store.imap.ImapClientInfo
import com.fsck.k9.mail.store.imap.ImapStore
import com.fsck.k9.mail.store.imap.ImapStoreConfig
import com.fsck.k9.mail.transport.smtp.SmtpTransport
import com.fsck.k9.mailstore.K9BackendStorageFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import net.thunderbird.core.android.account.AccountManager
import net.thunderbird.core.android.account.Expunge
import net.thunderbird.core.android.account.LegacyAccount

@Suppress("LongParameterList")
class ImapBackendFactory(
    private val accountManager: AccountManager,
    private val powerManager: PowerManager,
    private val idleRefreshManager: IdleRefreshManager,
    private val backendStorageFactory: K9BackendStorageFactory,
    private val trustedSocketFactory: TrustedSocketFactory,
    private val context: Context,
    private val clientInfoAppName: String,
    private val clientInfoAppVersion: String,
) : BackendFactory {
    override fun createBackend(account: LegacyAccount): Backend {
        val accountName = account.displayName
        val backendStorage = backendStorageFactory.createBackendStorage(account)
        val imapStore = createImapStore(account)
        val pushConfigProvider = createPushConfigProvider(account)
        val smtpTransport = createSmtpTransport(account)

        return ImapBackend(
            accountName,
            backendStorage,
            imapStore,
            powerManager,
            idleRefreshManager,
            pushConfigProvider,
            smtpTransport,
        )
    }

    private fun createImapStore(account: LegacyAccount): ImapStore {
        val serverSettings = account.toImapServerSettings()

        val oAuth2TokenProvider = if (serverSettings.authenticationType == AuthType.XOAUTH2) {
            createOAuth2TokenProvider(account)
        } else {
            null
        }

        val config = createImapStoreConfig(account)
        return ImapStore.create(
            serverSettings,
            config,
            trustedSocketFactory,
            oAuth2TokenProvider,
        )
    }

    private fun createImapStoreConfig(account: LegacyAccount): ImapStoreConfig {
        return object : ImapStoreConfig {
            override val logLabel
                get() = account.uuid

            override fun isSubscribedFoldersOnly() = account.isSubscribedFoldersOnly

            override fun isExpungeImmediately() = account.expungePolicy == Expunge.EXPUNGE_IMMEDIATELY

            override fun clientInfo() = ImapClientInfo(appName = clientInfoAppName, appVersion = clientInfoAppVersion)
        }
    }

    private fun createSmtpTransport(account: LegacyAccount): SmtpTransport {
        val serverSettings = account.outgoingServerSettings
        val oauth2TokenProvider = if (serverSettings.authenticationType == AuthType.XOAUTH2) {
            createOAuth2TokenProvider(account)
        } else {
            null
        }

        return SmtpTransport(serverSettings, trustedSocketFactory, oauth2TokenProvider)
    }

    private fun createOAuth2TokenProvider(account: LegacyAccount): RealOAuth2TokenProvider {
        val authStateStorage = AccountAuthStateStorage(accountManager, account)
        return RealOAuth2TokenProvider(context, authStateStorage)
    }

    private fun createPushConfigProvider(account: LegacyAccount) = object : ImapPushConfigProvider {
        override val maxPushFoldersFlow: Flow<Int>
            get() = accountManager.getAccountFlow(account.uuid)
                .filterNotNull()
                .map { it.maxPushFolders }
                .distinctUntilChanged()

        override val idleRefreshMinutesFlow: Flow<Int>
            get() = accountManager.getAccountFlow(account.uuid)
                .filterNotNull()
                .map { it.idleRefreshMinutes }
                .distinctUntilChanged()
    }
}
