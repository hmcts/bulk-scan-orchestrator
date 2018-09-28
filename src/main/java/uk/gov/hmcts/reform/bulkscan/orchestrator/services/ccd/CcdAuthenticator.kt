package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd

import uk.gov.hmcts.reform.idam.client.models.UserDetails
import java.util.function.Supplier
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * To support the easy interop with the java code supplier to the delegated property
 */
fun Supplier<String>.asReadOnlyProp(): ReadOnlyProperty<CcdAuthenticator, String> {
    return object : ReadOnlyProperty<CcdAuthenticator, String> {
        override fun getValue(thisRef: CcdAuthenticator, property: KProperty<*>): String {
            return this@asReadOnlyProp.get()
        }
    }
}

class CcdAuthenticator(
    serviceTokenSupplier: ReadOnlyProperty<CcdAuthenticator, String>,
    val userDetails: UserDetails,
    userTokenSupplier: ReadOnlyProperty<CcdAuthenticator, String>
) {

    val userToken: String by userTokenSupplier
    val serviceToken: String by serviceTokenSupplier

    companion object {
        @JvmStatic
        fun from(
            serviceTokenSupplier: Supplier<String>,
            userDetails: UserDetails,
            userTokenSupplier: Supplier<String>
        ): CcdAuthenticator {
            return CcdAuthenticator(
                serviceTokenSupplier.asReadOnlyProp(),
                userDetails,
                userTokenSupplier.asReadOnlyProp())
        }
    }
}
