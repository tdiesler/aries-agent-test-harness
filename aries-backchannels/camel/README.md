## Build ACA-Py + Camel Agents

```
./manage build -a acapy -a camel
```

## Start/Stop the TestHarness

```
./manage start -d acapy -b camel
...
./manage stop
```

## Run Tests

```
# Run a basic RFC0160 connection test using ACA-Py for Acme, Bob, Faber and Mallory
./manage test -d acapy -b camel -t @T001-RFC0160
```

## Aries Interop Profile 1.0 - Status

| Status | Feature: RFC 0025 DIDComm Transports
|:------:|:-------------------------------------|
|        |  @T001-RFC0025
|        |  Scenario: Create DIDExchange connection between two agents with overlapping transports
|        |  @T002-RFC0025
|        |  Scenario: Create 0160 connection between two agents with overlapping transports
|        |  @T003-RFC0025
|        |  Scenario: Fail creating a connection between two agents that have mismatching transports configured

| Status | Feature: RFC 0036 Aries agent issue credential
|:------:|:-----------------------------------------------|
|        |  @T001-RFC0036 @AIP10
|        |  Scenario: Issue a credential with the Holder beginning with a proposal
|        |  @T002-RFC0036 @AIP10
|        |  Scenario: Issue a credential with the Holder beginning with a proposal with negotiation
|        |  @T003-RFC0036 @AIP10
|        |  Scenario: Issue a credential with the Issuer beginning with an offer
|        |  @T004-RFC0036 @AIP10
|        |  Scenario: Issue a credential with the Issuer beginning with an offer with negotiation
|        |  @T005-RFC0036 @AIP10
|        |  Scenario: Issue a credential with negotiation beginning from a credential request
|        |  @T006-RFC0036 @AIP10
|        |  Scenario: Issue a credential with the Holder beginning with a request and is accepted

| Status | Feature: RFC 0037 Aries agent present proof
|:------:|:--------------------------------------------|
|        |  @T001-RFC0037 @AIP10
|        |  Scenario: Present Proof where the prover does not propose a presentation of the proof and is acknowledged
|        |  @T001.2-RFC0037 @AIP10
|        |  Scenario: Present Proof of specific types and proof is acknowledged with a Drivers License credential type
|        |  @T001.3-RFC0037 @AIP10
|        |  Scenario: Present Proof of specific types and proof is acknowledged with a Biological Indicators credential type
|        |  @T001.4-RFC0037 @AIP10
|        |  Scenario: Present Proof of specific types and proof is acknowledged with multiple credential types
|        |  @T001.5-RFC0037 @AIP10
|        |  Scenario: Present Proof where the prover does not propose a presentation of the proof and is acknowledged
|        |  @T003-RFC0037 @AIP10
|        |  Scenario: Present Proof where the prover has proposed the presentation of proof in response to a presentation request and is acknowledged
|        |  @T003.1-RFC0037 @AIP10
|        |  Scenario: Present Proof where the prover has proposed the presentation of proof from a different credential in response to a presentation request and is acknowledged
|        |  @T004-RFC0037 @AIP10
|        |  Scenario: Present Proof where the verifier and prover are connectionless, prover has proposed the presentation of proof, and is acknowledged
|        |  @T005-RFC0037 @AIP10
|        |  Scenario: Present Proof where the verifier rejects the presentation of the proof
|        |  @T006-RFC0037 @AIP10
|        |  Scenario: Present Proof where the prover starts with a proposal the presentation of proof and is acknowledged

| Status | Feature: RFC 0160 Aries agent connection functions
|:------:|:---------------------------------------------------|
|   x    |  @T001-RFC0160
|        |  Scenario: establish a connection between two agents
|        |  @T002-RFC0160
|        |  Scenario: Connection established between two agents but inviter sends next message to establish full connection state
|        |  @T003-RFC0160
|        |  Scenario: Inviter Sends invitation for one agent second agent tries after connection
|        |  @T004-RFC0160
|        |  Scenario: Inviter Sends invitation for one agent second agent tries during first share phase
|        |  @T005-RFC0160
|        |  Scenario: Inviter Sends invitation for multiple agents
|        |  @T006-RFC0160
|        |  Scenario: Establish a connection between two agents who already have a connection initiated from invitee
|        |  @T007-RFC0160
|        |  Scenario: Establish a connection between two agents but gets a request not accepted report problem message
