package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;

public class JpaRestfulServer extends BaseJpaRestfulServer {

  private static final long serialVersionUID = 1L;

  @Override
  protected void initialize() throws ServletException {
    super.initialize();

    // Add your own customization here
    org.springframework.context.ApplicationContext appCtx = (ApplicationContext) getServletContext()
      .getAttribute("org.springframework.web.context.WebApplicationContext.ROOT");

    //add device resource
    IFhirResourceDao<Device> deviceDao = appCtx.getBean("myDeviceDaoR4", IFhirResourceDao.class);

    Device thisSoftware = new Device();
    Device.DeviceDeviceNameComponent deviceName = new Device.DeviceDeviceNameComponent();
    deviceName.setName("Software: FH Hagenberg AIST: 'Process mining on FHIR' Server");
    thisSoftware.addDeviceName(deviceName);

    deviceDao.create(thisSoftware);


    //add plandefinition resource
    IFhirResourceDao<PlanDefinition> planDefDao = appCtx.getBean("myPlanDefinitionDaoR4", IFhirResourceDao.class);

    PlanDefinition planDefinition = new PlanDefinition();
    planDefinition.setId("rad-wf");
    planDefinition.setStatus(Enumerations.PublicationStatus.ACTIVE);
    planDefinition.setDescription("PlanDefinition of the radiology practice workflow");

    planDefDao.update(planDefinition);


  }

}
