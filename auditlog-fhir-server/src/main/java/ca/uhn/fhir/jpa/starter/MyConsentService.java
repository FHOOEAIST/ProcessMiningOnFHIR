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
import org.apache.commons.lang3.NotImplementedException;
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
  IFhirResourceDao<PlanDefinition> myPlanDefinitionDao;

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

    //retrieve initially created plandefinition by using empty search parameter map
    IBundleProvider allPlanDefinitions = myPlanDefinitionDao.search(new SearchParameterMap());
    PlanDefinition initiallyCreatedPlanDefinition;
    if (allPlanDefinitions != null && !allPlanDefinitions.isEmpty() && allPlanDefinitions.size() > 0) {
      initiallyCreatedPlanDefinition = (PlanDefinition) allPlanDefinitions.getResources(0, 1).get(0);
    } else {
      throw new ExceptionInInitializerError("Apparently no plandefinition has been created during initialization");
    }

    //add based on extension
    Extension basedOnExtension = new Extension();
    basedOnExtension.setUrl("http://aist.fh-hagenberg.at/fhir/extensions/auditevent-basedon-extension");
    basedOnExtension.setValue(new Reference(initiallyCreatedPlanDefinition));
    auditEvent.addExtension(basedOnExtension);

    AuditEvent.AuditEventAgentComponent agentComponent = new AuditEvent.AuditEventAgentComponent();
    agentComponent.setRequestor(false);

    //set who
    try {
      agentComponent.setWho(new Reference(retrieveSubjectIdOrPatientId(theRequestDetails)));
      CodeableConcept roleType = new CodeableConcept();
      roleType.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RoleClass", "PAT", "patient"));
      agentComponent.addRole(roleType);
    } catch (Exception e) {
      System.out.println(e);
    }

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

    auditEvent.addEntity(entityComponent);

    if (theRequestDetails.getRequestType().equals(RequestTypeEnum.POST)) {
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

  //retrieve the first subject reference id (patient id) of a resource (not exhaustive, only for resources of the tested radiological workflow)
  private String retrieveSubjectIdOrPatientId(RequestDetails theRequestDetails) {
    //TODO: figure out how to transer caseId for the operation  http://endpoint/DiagnosticReport/$DiagnosticReportId/$fhirToCDA

    // GET request
    if (theRequestDetails.getRequestType().equals("GET")) {
      //GET request that contains a subject parameter
      if (theRequestDetails.getParameters() != null &&
        !theRequestDetails.getParameters().isEmpty() &&
        theRequestDetails.getParameters().get("subject") != null) {
        return theRequestDetails.getParameters().get("subject")[0];
        //GET request that contains a patient's id
      } else if (theRequestDetails.getResourceName().equals("Patient") &&
        theRequestDetails.getId() != null &&
        theRequestDetails.getId().getValue() != null &&
        !theRequestDetails.getId().getValue().isEmpty()) {
        return theRequestDetails.getId().getValue();
      }
    }

    //PUT, POST, DELETE... or even GET request as long as it does not contain a parameter "subject"
    if (theRequestDetails.getResourceName().equals("Patient")) {
      return theRequestDetails.getId().getValue();
    } else if (theRequestDetails.getResourceName().equals("Appointment")) {
      Appointment appointment = (Appointment) theRequestDetails.getResource();
      return appointment.getParticipantFirstRep().getActor().getReference();
    } else if (theRequestDetails.getResourceName().equals("Procedure")) {
      Procedure procedure = (Procedure) theRequestDetails.getResource();
      return procedure.getSubject().getReference();
    } else if (theRequestDetails.getResourceName().equals("Media")) {
      Media media = (Media) theRequestDetails.getResource();
      return media.getSubject().getReference();
    } else if (theRequestDetails.getResourceName().equals("DiagnosticReport")) {
      DiagnosticReport diagnosticReport = (DiagnosticReport) theRequestDetails.getResource();
      return diagnosticReport.getSubject().getReference();
    } else {
      throw new NotImplementedException("There is no existing implementation yet to retrieve a subject for the resource " + theRequestDetails.getResourceName() + ".");
    }
  }
}