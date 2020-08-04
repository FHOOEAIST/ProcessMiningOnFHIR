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

import java.util.Arrays;
import java.util.Date;

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

  //Array toString with removed [brackets] so the result looks like: value1,value2,value3
  private String toCommaSeparatedStringList(String[] theInputStringArray) {
    if (theInputStringArray.length > 0) {
      String commaSeparatedListWithBrackets = Arrays.toString(theInputStringArray);
      return commaSeparatedListWithBrackets.substring(1, commaSeparatedListWithBrackets.length() - 1);
    } else {
      return "";
    }
  }
}