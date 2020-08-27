package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.interceptor.consent.ConsentOutcome;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentContextServices;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

public class MyConsentService implements IConsentService {

  @Autowired
  IFhirResourceDao<AuditEvent> myAuditEventDao;

  @Autowired
  IFhirResourceDao<Device> myDeviceDao;

  @Autowired
  IFhirResourceDao<DiagnosticReport> myDiagnosticReportDao;

  @Autowired
  IFhirResourceDao<PlanDefinition> myPlanDefinitionDao;

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MyConsentService.class);


  /**
   * Invoked once at the start of every request
   */
  @Override
  public ConsentOutcome startOperation(RequestDetails theRequestDetails, IConsentContextServices theContextServices) {
    return ConsentOutcome.AUTHORIZED;
  }

  /**
   * Can a given resource be returned to the user?
   */
  @Override
  public ConsentOutcome canSeeResource(RequestDetails theRequestDetails, IBaseResource theResource, IConsentContextServices theContextServices) {
    return ConsentOutcome.AUTHORIZED;
  }

  /**
   * Modify resources that are being shown to the user
   */
  @Override
  public ConsentOutcome willSeeResource(RequestDetails theRequestDetails, IBaseResource theResource, IConsentContextServices theContextServices) {
    return ConsentOutcome.AUTHORIZED;
  }

  @Override
  public void completeOperationSuccess(RequestDetails theRequestDetails, IConsentContextServices theContextServices) {
    createAuditEvent(theRequestDetails, true, null);
  }

  @Override
  public void completeOperationFailure(RequestDetails theRequestDetails, BaseServerResponseException theException, IConsentContextServices theContextServices) {
    createAuditEvent(theRequestDetails, false, theException.getMessage());
  }

  private void createAuditEvent(RequestDetails theRequestDetails, boolean wasSuccessful, String theException) {
    AuditEvent auditEvent = new AuditEvent();

    //decide what type of auditevent it is and set type accordingly
    if (theRequestDetails.getResourceName().equals("AuditEvent")) {
      auditEvent.setType(new Coding("Audit Log Used", "110101", "Audit event: Audit Log has been used"));
    } else if (theRequestDetails.getResourceName().equals("Patient")) {
      auditEvent.setType(new Coding("Patient Record", "110110", "Audit event: Patient Record has been created, read, updated, or deleted"));
    } else {
      auditEvent.setType(new Coding("Query", "110112", "Audit event: Query has been made"));
    }

    auditEvent.setRecorded(new Date());

    String encounterURL = "https://fhirserver.com/extensions/auditevent-encounter";
    //add based on extension for encounter
    Extension encounterExtension = new Extension();
    encounterExtension.setUrl(encounterURL);
    encounterExtension.setValue(retrieveEncounterId(theRequestDetails));
    auditEvent.addExtension(encounterExtension);


    //retrieve initially created plandefinition by using empty search parameter map
    IBundleProvider allPlanDefinitions = myPlanDefinitionDao.search(new SearchParameterMap());
    PlanDefinition initiallyCreatedPlanDefinition = null;
    if (allPlanDefinitions != null && !allPlanDefinitions.isEmpty() && allPlanDefinitions.size() > 0) {
      initiallyCreatedPlanDefinition = (PlanDefinition) allPlanDefinitions.getResources(0, 1).get(0);
    } else {
      logger.error("Apparently no plandefinition has been created during initialization");
    }

    //add based on extension for plandefinition
    String basedOnURL = "https://fhirserver.com/extensions/auditevent-basedon";
    Extension basedOnExtensionPlanDefinition = new Extension();
    basedOnExtensionPlanDefinition.setUrl(basedOnURL);
    basedOnExtensionPlanDefinition.setValue(new Reference(initiallyCreatedPlanDefinition));
    auditEvent.addExtension(basedOnExtensionPlanDefinition);


    AuditEvent.AuditEventAgentComponent agentComponent = new AuditEvent.AuditEventAgentComponent();
    agentComponent.setRequestor(false);
    auditEvent.addAgent(agentComponent);

    AuditEvent.AuditEventEntityComponent entityComponent = new AuditEvent.AuditEventEntityComponent();
    Reference referenceToSet = new Reference();
    if ((theRequestDetails.getId()) == null ||
      (theRequestDetails.getId()).isEmpty()) {
      //not a specific resource
      referenceToSet.setType(theRequestDetails.getResourceName());
    } else {
      // a specific instance
      referenceToSet.setId(theRequestDetails.getId().getValue());
    }
    entityComponent.setWhat(referenceToSet);


    String completeURL = theRequestDetails.getCompleteUrl();
    if (completeURL.contains("?")) {
      Base64BinaryType binaryTypeParameters = new Base64BinaryType();
      //read only parameters part (everything after the "?") and base64 encode them
      binaryTypeParameters.setValue(
        completeURL.split("[?]")[1]
          .getBytes());
      entityComponent.setQueryElement(binaryTypeParameters);
    }

    AuditEvent.AuditEventEntityDetailComponent detailsRequested = new AuditEvent.AuditEventEntityDetailComponent();
    detailsRequested.setType("RequestedURL");
    detailsRequested.setValue(new StringType(completeURL));
    entityComponent.addDetail(detailsRequested);

    auditEvent.addEntity(entityComponent);

    if (theRequestDetails.getOperation() != null && !theRequestDetails.getOperation().isEmpty()) {
      auditEvent.setAction(AuditEvent.AuditEventAction.E);
    } else if (theRequestDetails.getRequestType().equals(RequestTypeEnum.POST)) {
      auditEvent.setAction(AuditEvent.AuditEventAction.C);
    } else if (theRequestDetails.getRequestType().equals(RequestTypeEnum.GET)) {
      auditEvent.setAction(AuditEvent.AuditEventAction.R);
    } else if (theRequestDetails.getRequestType().equals(RequestTypeEnum.DELETE)) {
      auditEvent.setAction(AuditEvent.AuditEventAction.D);
    } else if (theRequestDetails.getRequestType().equals(RequestTypeEnum.PUT)) {
      auditEvent.setAction(AuditEvent.AuditEventAction.U);
    }
    //TODO: ...more action codes if needed

    if (wasSuccessful) {
      auditEvent.setOutcome(AuditEvent.AuditEventOutcome._0);
    } else {
      //TODO: figure out more severe errors
      auditEvent.setOutcome(AuditEvent.AuditEventOutcome._4);
      auditEvent.setOutcomeDesc(theException);
    }

    //retrieve initially created device by using empty search parameter map
    IBundleProvider allDevices = myDeviceDao.search(new SearchParameterMap());
    Device thisSoftware;
    if (allDevices != null && !allDevices.isEmpty() && allDevices.size() > 0) {
      thisSoftware = (Device) allDevices.getResources(0, 1).get(0);
    } else {
      throw new ExceptionInInitializerError("Apparently no device has been created during initialization");
    }

    AuditEvent.AuditEventSourceComponent sourceComponent = new AuditEvent.AuditEventSourceComponent();
    sourceComponent.setObserver(new Reference(thisSoftware));

    auditEvent.setSource(sourceComponent);

    myAuditEventDao.create(auditEvent);
  }

  //retrieve the encounter id of a resource (not exhaustive, only for resources of the tested radiological workflow)
  private Reference retrieveEncounterId(RequestDetails theRequestDetails) {
    //DiagnosticReport operation
    if (theRequestDetails.getRequestType().equals(RequestTypeEnum.GET) &&
      theRequestDetails.getOperation() != null &&
      !theRequestDetails.getOperation().isEmpty() &&
      theRequestDetails.getResourceName().equals("DiagnosticReport") &&
      theRequestDetails.getId() != null &&
      !theRequestDetails.getId().isEmpty()) {

      DiagnosticReport diagnosticReport = myDiagnosticReportDao.read(theRequestDetails.getId());
      return diagnosticReport.getEncounter();
    } else if (theRequestDetails.getResourceName().equals("Appointment")) {
      Appointment appointment = (Appointment) theRequestDetails.getResource();
      Extension encounterExtension = appointment.getExtensionByUrl("http://aist.fh-hagenberg.at/fhir/extensions/appointment-encounter-extension");
      return (Reference) encounterExtension.getValue();
    } else if (theRequestDetails.getResourceName().equals("Procedure")) {
      Procedure procedure = (Procedure) theRequestDetails.getResource();
      return procedure.getEncounter();
    } else if (theRequestDetails.getResourceName().equals("Media")) {
      Media media = (Media) theRequestDetails.getResource();
      return media.getEncounter();
      //this is executed, if it is not a GET request with an operation on a specific DiagnosticReport but any other manipulation or usage of this resource
    } else if (theRequestDetails.getResourceName().equals("DiagnosticReport")) {
      DiagnosticReport diagnosticReport = (DiagnosticReport) theRequestDetails.getResource();
      return diagnosticReport.getEncounter();
    } else if (
      theRequestDetails.getResourceName().equals("AuditEvent") ||
        theRequestDetails.getResourceName().equals("Patient") ||
        theRequestDetails.getResourceName().equals("Encounter")
    ) {
      //nothing. there is no need to retrieve an encounterId for auditevents. patients or encounters. also, these resources do ned have an encounter element
      return null;
    } else {
      throw new UnsupportedOperationException("There is no existing implementation yet to retrieve an encounter reference for the resource " + theRequestDetails.getResourceName() + ".");
    }
  }
}