package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.CachedIdamCredential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.IdamCachedClient;

@Service
public class CcdAuthenticatorFactory {

    private static final Logger log = LoggerFactory.getLogger(CcdAuthenticatorFactory.class);
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamCachedClient idamClient;

    public CcdAuthenticatorFactory(
        AuthTokenGenerator s2sTokenGenerator,
        IdamCachedClient idamClient
    ) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.idamClient = idamClient;
    }

    public CcdAuthenticator createForJurisdiction(String jurisdiction) {
        CachedIdamCredential idamCredentials = idamClient.getIdamCredentials(jurisdiction);
        try {

            return new CcdAuthenticator(
                s2sTokenGenerator::generate,
                idamCredentials.userId,
                idamCredentials.accessToken
            );
        }
        catch(Exception e){
            return  null;
        }
    }

    public void removeFromCache(String jurisdiction) {
        idamClient.removeAccessTokenFromCache(jurisdiction);
    }
}
