# ProcessMiningOnFHIR
Enabling Mining of Processes in HL7 FHIR

Notes about the server:
The auditlog-fhir-server is based on the HAPI-FHIR Starter Project with a detailed description on how to use it here:
https://github.com/FHOOEAIST/ProcessMiningOnFHIR/blob/master/auditlog-fhir-server/README.md and here: https://hapifhir.io/

We started the server using the maven jetty plugin and the following command: ```mvn clean jetty:run```

Initially, on server start up (class JpaRestfulServer), two resources are created that later on are automatically retrieved and used for every AuditEvent: Device and PlanDefinition.

Two provider classes define our custom FHIR Operations $xes (class XESProvider) and $fhirToCDA (CDAProvider).

The ConsentService is responsible to create an AuditEvent all the time a CRUD operation has been finished successfully or with a failure. Please note that our AuditEvent requires an Encounter reference. If that reference cannot be retrieved automatically, an error log will show up.
