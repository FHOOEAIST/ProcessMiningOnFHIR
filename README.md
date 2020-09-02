# ProcessMiningOnFHIR
Enabling Mining of Processes in HL7 FHIR

## Notes about the server

### Documentation
The auditlog-fhir-server is based on the HAPI-FHIR Starter Project. A detailed description of how to start up and use the server project can be found in the [README file of the audit-log-server](https://github.com/FHOOEAIST/ProcessMiningOnFHIR/blob/master/auditlog-fhir-server/README.md). If you want to read more into the HAPI FHIR library itself, here you can find the [HAPI FHIR documentation](https://hapifhir.io/).

### Notes about implementation
Initially, on server start up (class [JpaRestfulServer](https://github.com/FHOOEAIST/ProcessMiningOnFHIR/blob/master/auditlog-fhir-server/src/main/java/ca/uhn/fhir/jpa/starter/JpaRestfulServer.java)), two resources are created that later on are automatically retrieved and used for every AuditEvent: Device and PlanDefinition.

Two provider classes define our custom FHIR Operations $xes (class [XESProvider](https://github.com/FHOOEAIST/ProcessMiningOnFHIR/blob/master/auditlog-fhir-server/src/main/java/ca/uhn/fhir/jpa/starter/XESProvider.java)) and $fhirToCDA ([CDAProvider](https://github.com/FHOOEAIST/ProcessMiningOnFHIR/blob/master/auditlog-fhir-server/src/main/java/ca/uhn/fhir/jpa/starter/CDAProvider.java)).

The [ConsentService](https://github.com/FHOOEAIST/ProcessMiningOnFHIR/blob/master/auditlog-fhir-server/src/main/java/ca/uhn/fhir/jpa/starter/MyConsentService.java) is responsible to create an AuditEvent anytime a CRUD operation has been finished successfully or with a failure. Please note that for our use case each AuditEvent requires a resource reference to an Encounter. If that reference cannot be retrieved automatically (either, because a resource doesn't contain an Encounter or a resource is being used that is not known to the retrieveEncounterId function), an error log will be produced.
