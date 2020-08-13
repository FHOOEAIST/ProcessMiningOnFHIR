package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.github.dnault.xmlpatch.repackaged.org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provider that adds operations for retrieving XES
 *
 * @author Oliver Krauss on 06.08.2020
 */
public class XESProvider implements IResourceProvider {

  /**
   * A registry of data access objects used to talk to the back end data store
   */
  @Autowired
  IFhirResourceDao<AuditEvent> myAuditEventDao;

  @Autowired
  IFhirResourceDao<Device> myDeviceDao;

  @Autowired
  IFhirResourceDao<PlanDefinition> myPlanDefinitionDao;

  /**
   * Returns XES with the given request
   *
   * @param theServletRequest  request from client
   * @param theServletResponse XES data as response
   * @throws IOException
   */
  @Operation(name = "$xes", manualResponse = true, manualRequest = true, idempotent = true)
  public void xesTypeOperation(HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) throws IOException {
    String contentType = theServletRequest.getContentType();
    byte[] bytes = IOUtils.toByteArray(theServletRequest.getInputStream());

    theServletResponse.setContentType("text/plain");

    theServletResponse.getWriter().write(generateXES());
    theServletResponse.getWriter().close();
  }

  /**
   * Simple helper function that generates the XES. As the functionality is currently "in motion" with several open change requests in FHIR we are
   * String concatenating instead of a template engine.
   *
   * @return XES XML file as plain text.
   */
  private String generateXES() {

    Map<String, List<AuditEvent>> tracesPerPatient = new HashMap<>();

    IBundleProvider search = myAuditEventDao.search(SearchParameterMap.newSynchronous());
    List<IBaseResource> resources = search.getResources(0, search.size());
    resources.forEach(x -> {
      if (x != null && x instanceof AuditEvent) {
        AuditEvent event = (AuditEvent) x;
        // NOTE: Currently we only accept The Radiology Workflow
        String caseId;
        if (event.hasExtension("http://aist.fh-hagenberg.at/fhir/extensions/auditevent-basedon-extension") &&
          (caseId = ((Reference) event.getExtensionByUrl("http://aist.fh-hagenberg.at/fhir/extensions/auditevent-basedon-extension").getValue()).getReference()) != null) {
          if (!tracesPerPatient.containsKey(caseId)) {
            tracesPerPatient.put(caseId, new LinkedList<>());
          }
          tracesPerPatient.get(caseId).add(event);

        }
      }
    });


    return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "\n" +
      "<log xes.version=\"1.0\" xes.features=\"nested-attributes\" openxes.version=\"1.0RC7\" xmlns=\"http://www.xes-standard.org/\">\n" +
      "\t<extension name=\"Organizational\" prefix=\"org\" uri=\"http://www.xes-standard.org/org.xesext\"/>\n" +
      "\t<extension name=\"Time\" prefix=\"time\" uri=\"http://www.xes-standard.org/time.xesext\"/>\n" +
      "\t<extension name=\"Lifecycle\" prefix=\"lifecycle\" uri=\"http://www.xes-standard.org/lifecycle.xesext\"/>\n" +
      "\t<extension name=\"Concept\" prefix=\"concept\" uri=\"http://www.xes-standard.org/concept.xesext\"/>\n" +
      "\n" +
      "\t<string key=\"concept:name\" value=\"Radiology Workflow\"/>\n" +
      "\n" +
      tracesPerPatient.entrySet().stream().map(x -> trace(x.getKey(), x.getValue())).collect(Collectors.joining("\n")) +
      "</log>\n";
  }

  /**
   * Creates one trace per patient
   *
   * @param caseId patient case id
   * @param events events for that patient
   * @return trace string
   */
  private String trace(String caseId, List<AuditEvent> events) {
    return "\t<trace>\n" +
      "\t\t<string key=\"concept:name\" value=\"" + caseId + "\"/>\n" +
      events.stream().map(x -> mapEvent(x)).collect(Collectors.joining("\n")) +
      "\t</trace>\n\n";
  }

  /**
   * Maps events to XES. This is completely un-reusable as we cheat by mapping KNOWN actions and Resources back to the correct task names
   *
   * @param x event to be mapped
   * @return string of event
   */
  private String mapEvent(AuditEvent x) {
    String event = "";

    switch (x.getAction().name()) {
      case "C":
        switch (x.getEntityFirstRep().getWhat().getType()) {
          case "Appointment":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Schedule Appointment\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
          case "Media":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Diagnosis\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
          case "DiagnosticReport":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Report Writing\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
        }
        break;
      case "U":
        switch (x.getEntityFirstRep().getWhat().getType()) {
          case "Appointment":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Patient Admission\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
          case "Procedure":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Radiological Examination\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
          case "DiagnosticReport":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Report Attestation\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
        }
        break;
      case "E":
        switch (x.getEntityFirstRep().getDetailFirstRep().getValue().toString()) {
          case "*$fhirToCDA":
            event = "<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Report Transmission\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + x.getRecorded().toString() + "\"/>\n" +
              "\t\t</event>";
            break;
        }
        break;
    }

    return event;
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return AuditEvent.class;
  }
}
