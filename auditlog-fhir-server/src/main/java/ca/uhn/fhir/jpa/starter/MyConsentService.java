package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.interceptor.consent.ConsentOutcome;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentContextServices;
import ca.uhn.fhir.rest.server.interceptor.consent.IConsentService;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MyConsentService implements IConsentService {

  @Autowired
  IFhirResourceDao<AuditEvent> myAuditEventDao;

  @Autowired
  IFhirResourceDao<Device> myDeviceDao;

  /**
   * Invoked once at the start of every request
   */
  @Override
  public ConsentOutcome startOperation(RequestDetails theRequestDetails, IConsentContextServices theContextServices) {
    //createAuditEvent(theRequestDetails);
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
    auditEvent.setType(new Coding("Query", "110112", "Audit event: Query has been made"));
    auditEvent.setRecorded(new Date());

    AuditEvent.AuditEventAgentComponent agentComponent = new AuditEvent.AuditEventAgentComponent();
    agentComponent.setRequestor(true);
    auditEvent.addAgent(agentComponent);

    AuditEvent.AuditEventEntityComponent entityComponent = new AuditEvent.AuditEventEntityComponent();
    //TODO: what if it was a query for not a specific resource?
    entityComponent.setWhat(new Reference((IAnyResource) theRequestDetails.getResource()));

    //TODO: what if no parameters?
    Base64BinaryType binaryTypeParameters = new Base64BinaryType();
    //converting key value of map to a simple string
    Map<String, String[]> parameters = theRequestDetails.getParameters();
    binaryTypeParameters.setValueAsString(
      parameters
      .keySet().stream()
      .map(key -> key + "=" + parameters.get(key).toString())
      .collect(Collectors.joining("&", "?", "")));
    entityComponent.setQueryElement(binaryTypeParameters);


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

    Device thisSoftware = new Device();
    Device.DeviceDeviceNameComponent deviceName = new Device.DeviceDeviceNameComponent();
    deviceName.setName("Process mining AuditEvent FHIR Server");
    thisSoftware.addDeviceName(deviceName);

    myDeviceDao.create(thisSoftware);

    AuditEvent.AuditEventSourceComponent sourceComponent = new AuditEvent.AuditEventSourceComponent();
    sourceComponent.setObserver(new Reference(thisSoftware));

    myAuditEventDao.create(auditEvent);
  }
}