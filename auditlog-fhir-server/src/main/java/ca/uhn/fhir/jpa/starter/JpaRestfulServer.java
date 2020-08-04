package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import org.hl7.fhir.r4.model.Device;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;

public class JpaRestfulServer extends BaseJpaRestfulServer {

  private static final long serialVersionUID = 1L;

  @Override
  protected void initialize() throws ServletException {
    super.initialize();

    org.springframework.context.ApplicationContext appCtx = (ApplicationContext) getServletContext()
      .getAttribute("org.springframework.web.context.WebApplicationContext.ROOT");

    IFhirResourceDao<Device> deviceDao = appCtx.getBean("myDeviceDaoR4", IFhirResourceDao.class);


    // Add your own customization here

    //add device resource
    Device thisSoftware = new Device();
    Device.DeviceDeviceNameComponent deviceName = new Device.DeviceDeviceNameComponent();
    deviceName.setName("Software: FH Hagenberg AIST: 'Process mining on FHIR' Server");
    thisSoftware.addDeviceName(deviceName);

    deviceDao.create(thisSoftware);


    //TODO: add plandefinition resource

  }

}
