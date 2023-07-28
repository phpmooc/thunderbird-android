package app.k9mail.feature.account.setup.ui.incoming

import app.k9mail.feature.account.setup.domain.entity.IncomingProtocolType
import app.k9mail.feature.account.setup.domain.entity.toAuthType
import app.k9mail.feature.account.setup.domain.entity.toMailConnectionSecurity
import app.k9mail.feature.account.setup.ui.validation.AccountValidationContract
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.store.imap.ImapStoreSettings

internal fun AccountIncomingConfigContract.State.toServerSettings(): ServerSettings {
    return ServerSettings(
        type = protocolType.defaultName,
        host = server.value,
        port = port.value!!.toInt(),
        connectionSecurity = security.toMailConnectionSecurity(),
        authenticationType = authenticationType.toAuthType(),
        username = username.value,
        password = if (authenticationType.isPasswordRequired) password.value else null,
        clientCertificateAlias = clientCertificateAlias,
        extra = createExtras(),
    )
}

private fun AccountIncomingConfigContract.State.createExtras(): Map<String, String?> {
    return if (protocolType == IncomingProtocolType.IMAP) {
        ImapStoreSettings.createExtra(
            autoDetectNamespace = imapAutodetectNamespaceEnabled,
            pathPrefix = if (imapAutodetectNamespaceEnabled) null else imapPrefix.value,
            useCompression = imapUseCompression,
            sendClientId = imapSendClientId,
        )
    } else {
        emptyMap()
    }
}

internal fun AccountIncomingConfigContract.State.toValidationState(): AccountValidationContract.State {
    return AccountValidationContract.State(
        serverSettings = toServerSettings(),
        // TODO add authorization state
    )
}
