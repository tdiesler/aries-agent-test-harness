package io.nessus.aries.aath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.hyperledger.aries.api.multitenancy.WalletRecord;

import io.nessus.aries.wallet.WalletRegistry;

public class WebSocketEventHandler extends DefaultEventHandler {
    
    private Map<String, V1CredentialExchange> credexV1 = new LinkedHashMap<>();
    
    public WebSocketEventHandler(WalletRecord thisWallet, WalletRegistry walletRegistry) {
        super(thisWallet, walletRegistry);
    }

    @Override
    public V1CredentialExchange handleIssueCredentialV1(WebSocketEvent ev) throws Exception {
        V1CredentialExchange credex = super.handleIssueCredentialV1(ev);
        credexV1.put(credex.getThreadId(), credex);
        return credex;
    }

    public List<V1CredentialExchange> getCredentialExchangesV1() {
        return Collections.unmodifiableList(new ArrayList<>(credexV1.values()));
    }
    
    public List<V1CredentialExchange> getCredentialExchangesV1(Predicate<V1CredentialExchange> filter) {
        return getCredentialExchangesV1().stream().filter(filter).collect(Collectors.toList());
    }
    
    public V1CredentialExchange removeCredentialExchangesV1(String threadId) {
        return credexV1.remove(threadId);
    }
    
}