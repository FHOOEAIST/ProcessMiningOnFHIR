package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MyConsentService.class);

  /**
   * Returns XES with the given request
   *
   * @param thePlandefinitionReference reference to plandefinition which is used to retrieve a list of AudtEvents
   * @param theServletResponse         XES data as response
   * @throws IOException
   */
  @Operation(name = "$xes", manualResponse = true, manualRequest = true, idempotent = true)
  public void xesTypeOperation(@OperationParam(name = "plandefinition") ReferenceParam thePlandefinitionReference, HttpServletResponse theServletResponse) throws IOException {
    if (
      thePlandefinitionReference == null ||
        thePlandefinitionReference.getResourceType() == null ||
        !thePlandefinitionReference.getResourceType().equals("PlanDefinition")) {
      logger.error("$xes operation called with invalid resource type parameter.");
      theServletResponse.setStatus(400);
    } else {
      theServletResponse.setStatus(200);
      theServletResponse.setContentType("text/plain");

      theServletResponse.getWriter().write(generateXES(thePlandefinitionReference));
      theServletResponse.getWriter().close();
    }
  }

  /**
   * Simple helper function that generates the XES. As the functionality is currently "in motion" with several open change requests in FHIR we are
   * String concatenating instead of a template engine.
   *
   * @return XES XML file as plain text.
   */
  private String generateXES(ReferenceParam thePlandefinitionReference) {

    Map<String, List<AuditEvent>> tracesPerPatient = new HashMap<>();

    IBundleProvider search = myAuditEventDao.search(SearchParameterMap.newSynchronous());
    List<IBaseResource> resources = search.getResources(0, search.size());
    resources.forEach(x -> {
      if (x instanceof AuditEvent) {
        AuditEvent event = (AuditEvent) x;
        // NOTE: Currently we only accept The Radiology Workflow
        String caseId;
        if (
          event.hasExtension("https://fhirserver.com/extensions/auditevent-basedon") &&
            ((Reference) event.getExtensionByUrl("https://fhirserver.com/extensions/auditevent-basedon").getValue()).getReference().equals("PlanDefinition/" + thePlandefinitionReference.getIdPart()) &&
            event.hasExtension("https://fhirserver.com/extensions/auditevent-encounter") &&
            (caseId = ((Reference) event.getExtensionByUrl("https://fhirserver.com/extensions/auditevent-encounter").getValue()).getReference()) != null) {
          if (!tracesPerPatient.containsKey(caseId)) {
            tracesPerPatient.put(caseId, new LinkedList<>());
          }
          tracesPerPatient.get(caseId).add(event);

        }
      }
    });


    return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
      "\n" +
      "<log xes.version=\"1.0\" xes.features=\"\" openxes.version=\"1.0RC7\" xmlns=\"http://www.xes-standard.org/\">\n" +
      "\t<extension name=\"Organizational\" prefix=\"org\" uri=\"http://www.xes-standard.org/org.xesext\"/>\n" +
      "\t<extension name=\"Time\" prefix=\"time\" uri=\"http://www.xes-standard.org/time.xesext\"/>\n" +
      "\t<extension name=\"Lifecycle\" prefix=\"lifecycle\" uri=\"http://www.xes-standard.org/lifecycle.xesext\"/>\n" +
      "\t<extension name=\"Concept\" prefix=\"concept\" uri=\"http://www.xes-standard.org/concept.xesext\"/>\n" +
      "\n" +
      "\t<string key=\"concept:name\" value=\"PlanDefinition/" + thePlandefinitionReference.getIdPart() + "\"/>\n" +
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
      events.stream().map(this::mapEvent).collect(Collectors.joining("\n")) +
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
            event = "\t\t<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Schedule Appointment\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
              "\t\t</event>";
            break;
          case "Media":
            event = "\t\t<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Diagnosis\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
              "\t\t</event>";
            break;
          case "DiagnosticReport":
            event = "\t\t<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Report Writing\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
              "\t\t</event>";
            break;
        }
        break;
      case "U":
        switch (x.getEntityFirstRep().getWhat().getType()) {
          case "Appointment":
            event = "\t\t<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Patient Admission\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
              "\t\t</event>";
            break;
          case "Procedure":
            event = "\t\t<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Radiological Examination\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
              "\t\t</event>";
            break;
          case "DiagnosticReport":
            event = "\t\t<event>\n" +
              "\t\t\t<string key=\"concept:name\" value=\"Report Attestation\"/>\n" +
              "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
              "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
              "\t\t</event>";
            break;
        }
        break;
      case "E":
        if (x.getEntityFirstRep().getDetailFirstRep().getValue().toString().endsWith("$fhirToCDA")) {
          event = "\t\t<event>\n" +
            "\t\t\t<string key=\"concept:name\" value=\"Report Transmission\"/>\n" +
            "\t\t\t<string key=\"lifecycle:transition\" value=\"complete\"/>\n" +
            "\t\t\t<date key=\"time:timestamp\" value=\"" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(x.getRecorded().toInstant().atZone(ZoneId.systemDefault())) + "\"/>\n" +
            "\t\t</event>";
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
