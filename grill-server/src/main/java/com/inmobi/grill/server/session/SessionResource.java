package com.inmobi.grill.server.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hive.service.cli.HiveSQLException;
import org.apache.hive.service.cli.OperationHandle;
import org.apache.hive.service.cli.RowSet;
import org.apache.hive.service.cli.thrift.TRow;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.inmobi.grill.api.APIResult;
import com.inmobi.grill.api.GrillConf;
import com.inmobi.grill.api.GrillException;
import com.inmobi.grill.api.GrillSessionHandle;
import com.inmobi.grill.api.StringList;
import com.inmobi.grill.server.GrillService;
import com.inmobi.grill.server.GrillServices;

/**
 * Session resource api
 * 
 * This provides api for all things in session.
 */
@Path("/session")
public class SessionResource {
  public static final Log LOG = LogFactory.getLog(SessionResource.class);
  private HiveSessionService sessionService;

  /**
   * API to know if session service is up and running
   * 
   * @return Simple text saying it up
   */
  @GET
  @Produces({MediaType.TEXT_PLAIN})
  public String getMessage() {
    return "session is up!";
  }

  public SessionResource() throws GrillException {
    sessionService = (HiveSessionService)GrillServices.get().getService("session");
  }

  /**
   * Create a new session with Grill server
   * 
   * @param username User name of the Grill server user
   * @param password Password of the Grill server user
   * @param sessionconf Key-value properties which will be used to configure this session
   * 
   * @return A Session handle unique to this session
   * 
   * @throws WebApplicationException if there was an exception thrown while creating the session
   */
  @POST
  @Consumes({MediaType.MULTIPART_FORM_DATA})
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
  public GrillSessionHandle openSession(@FormDataParam("username") String username,
      @FormDataParam("password") String password,
      @FormDataParam("sessionconf") GrillConf sessionconf) {
    try {
      Map<String, String> conf;
      if (sessionconf != null) {
        conf = sessionconf.getProperties();
      } else{
        conf = new HashMap<String, String>();
      }
      return sessionService.openSession(username, password, conf);
    } catch (GrillException e) {
      throw new WebApplicationException(e);
    }
  }

  /**
   * Close a Grill server session 
   * 
   * @param sessionid Session handle object of the session to be closed
   * 
   * @return APIResult object indicating if the operation was successful (check result.getStatus())
   * 
   * @throws WebApplicationException if the underlying CLIService threw an exception 
   * while closing the session
   * 
   */
  @DELETE
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
  public APIResult closeSession(@QueryParam("sessionid") GrillSessionHandle sessionid) {
    try {
      sessionService.closeSession(sessionid);
    } catch (GrillException e) {
      return new APIResult(APIResult.Status.FAILED, e.getMessage());
    }
    return new APIResult(APIResult.Status.SUCCEEDED,
        "Close session with id" + sessionid + "succeeded");
  }

  /**
   * Add a resource to the session to all GrillServices running in this Grill server
   * 
   * <p>
   * The returned @{link APIResult} will have status SUCCEEDED <em>only if</em> the add operation
   * was successful for all services running in this Grill server.
   * </p>
   * 
   * @param sessionid session handle object
   * @param type The type of resource. Valid types are 'jar', 'file' and 'archive'
   * @param path path of the resource
   * @return {@link APIResult} with state {@link APIResult.Status#SUCCEEDED}, if add was successful.
   * {@link APIResult} with state {@link APIResult.Status#PARTIAL}, if add succeeded only for some services.
   * {@link APIResult} with state {@link APIResult.Status#FAILED}, if add has failed
   */
  @PUT
  @Path("resources/add")
  @Consumes({MediaType.MULTIPART_FORM_DATA})
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
  public APIResult addResource(@FormDataParam("sessionid") GrillSessionHandle sessionid,
      @FormDataParam("type") String type, @FormDataParam("path") String path) {
    int numAdded = 0;
    for (GrillService service : GrillServices.get().getGrillServices()) {
      try {
        service.addResource(sessionid,  type, path);
        numAdded++;
      } catch (GrillException e) {
        LOG.error("Failed to add resource in service:" + service, e);
        if (numAdded != 0) { 
          return new APIResult(APIResult.Status.PARTIAL,
              "Add resource is partial, failed for service:" + service.getName());
        } else {
          return new APIResult(APIResult.Status.FAILED,
              "Add resource has failed ");          
        }
      }
    }
    return new APIResult(APIResult.Status.SUCCEEDED,
        "Add resource succeeded");
  }

  /**
   * Delete a resource from sesssion from all the @{link GrillService}s running in this Grill server
   * <p>
   * Similar to addResource, this call is successful only if resource was deleted from all services.
   * </p>
   * 
   * @param sessionid session handle object
   * @param type The type of resource. Valid types are 'jar', 'file' and 'archive'
   * @param path path of the resource to be deleted
   * 
   * @return {@link APIResult} with state {@link APIResult.Status#SUCCEEDED}, if delete was successful.
   * {@link APIResult} with state {@link APIResult.Status#PARTIAL}, if delete succeeded only for some services.
   * {@link APIResult} with state {@link APIResult.Status#FAILED}, if delete has failed
   */
  @PUT
  @Path("resources/delete")
  @Consumes({MediaType.MULTIPART_FORM_DATA})
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
  public APIResult deleteResource(@FormDataParam("sessionid") GrillSessionHandle sessionid,
      @FormDataParam("type") String type, @FormDataParam("path") String path) {
    int numDeleted = 0;
    for (GrillService service : GrillServices.get().getGrillServices()) {
      try {
        service.deleteResource(sessionid,  type, path);
        numDeleted++;
      } catch (GrillException e) {
        LOG.error("Failed to delete resource in service:" + service, e);
        if (numDeleted != 0) {
          return new APIResult(APIResult.Status.PARTIAL,
              "Delete resource is partial, failed for service:" + service.getName());
        } else {
          return new APIResult(APIResult.Status.PARTIAL,
              "Delete resource has failed");
        }
      }
    }
    return new APIResult(APIResult.Status.SUCCEEDED,
        "Delete resource succeeded");
  }

  /**
   * Get a list of key=value parameters set for this session
   * 
   * @param sessionid session handle object
   * @param verbose If true, all the parameters will be returned.
   *  If false, configuration parameters will be returned
   * @param key if this is empty, output will contain all parameters and their values, 
   * if it is non empty parameters will be filtered by key
   * 
   * @return List of Strings, one entry per key-value pair
   */
  @GET
  @Path("params")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
  public StringList getParams(@QueryParam("sessionid") GrillSessionHandle sessionid,
      @DefaultValue("false") @QueryParam("verbose") boolean verbose,
      @DefaultValue("") @QueryParam("key") String key) {
    OperationHandle handle = sessionService.getAllSessionParameters(sessionid, verbose, key);
    RowSet rows = null;
    try {
      rows = sessionService.getCliService().fetchResults(handle);
    } catch (HiveSQLException e) {
      new WebApplicationException(e);
    }
    List<String> result = new ArrayList<String>();
    for (TRow row : rows.toTRowSet().getRows()) {
      result.add(row.getColVals().get(0).getStringVal().getValue()); 
    }
    return new StringList(result);
  }

  /**
   * Set value for a parameter specified by key
   * 
   * The parameters can be a system property or a hive variable or a configuration.
   * To set key as system property, the key should be prefixed with 'system:'.
   * To set key as a hive variable, the key should be prefixed with 'hivevar:'.
   * To set key as configuration parameter, the key should be prefixed with 'hiveconf:'.
   * If no prefix is attached, the parameter is set as configuration.
   * 
   * System properties are not restricted to the session, they would be set globally
   * 
   * @param sessionid session handle object
   * @param key parameter key
   * @param value parameter value
   * 
   * @return APIResult object indicating if set operation was successful
   */
  @PUT
  @Path("params")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
  public APIResult setParam(@FormDataParam("sessionid") GrillSessionHandle sessionid,
      @FormDataParam("key") String key, @FormDataParam("value") String value) {
    sessionService.setSessionParameter(sessionid, key, value);
    return new APIResult(APIResult.Status.SUCCEEDED, "Set param succeeded");
  }

}
