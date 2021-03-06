package cs.saip.ipc.rest;

import static spark.Spark.*;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.*;

import cs.saip.broker.*;
import cs.saip.domain.*;
import cs.saip.ipc.http.Constants;
import cs.saip.storage.XDSBackend;

/** A REST based implementation of the ServerRequestHandler and Invoker
 * roles using Spark-Java. 
 * <p>
 * As a REST servlet handles IPC as well as the location aspects of the
 * Invoker, it makes the code much shorter and easier to follow to merge
 * the two roles.
 * <p>
 * NOTE: It also implements the Invoker role, but it is not mentioned in the
 * implements list as the 'handleRequest' method is only indirectly implemented
 * (through registering routes in Spark).
 * 
 * @author Henrik Baerbak Christensen, Aarhus University.
 *
 */
public class RESTServerRequestHandlerInvoker
    implements ServerRequestHandler {

  private TeleMed teleMed;
  private int port;
  private Gson gson;

  public RESTServerRequestHandlerInvoker(int portNumber,
      TeleMed tsServant, XDSBackend xds) {
    this.teleMed = tsServant;
    this.port = portNumber;

    gson = new Gson();
  }

  public void registerRoutes() {
    // Set the port to listen to
    port(port);

    // POST = processAndStore
    String storeRoute = "/" + Constants.BLOODPRESSURE_PATH;
    post(storeRoute, (req, res) -> {
      String body = req.body();

      // Demarshal parameters into a JsonArray
      TeleObservation teleObs = gson.fromJson(body, TeleObservation.class);
      String id = teleMed.processAndStore(teleObs);

      int statusCode = HttpServletResponse.SC_CREATED;

      // Normally: 201 Created
      res.status(statusCode);
      res.type(Constants.APPLICATION_JSON);

      // Location = URL of created resource
      res.header("Location",
          req.host() + "/" + Constants.BLOODPRESSURE_PATH + id);

      // Return the tele observation as confirmation
      return gson.toJson(teleObs);
    });

    // GET = getObservation
    String getRoute = "/" + Constants.BLOODPRESSURE_PATH + ":id";

    get(getRoute, (req, res) -> {
      String uniqueId = req.params(":id");

      TeleObservation teleObs = teleMed.getObservation(uniqueId);

      String returnValue = null;
      int lastStatusCode;
      
      if (teleObs == null) {
        lastStatusCode = HttpServletResponse.SC_NOT_FOUND;
        returnValue = "{}"; // a null JSON object
      } else {
        lastStatusCode = HttpServletResponse.SC_OK;
        returnValue = gson.toJson(teleObs);
      }

      res.status(lastStatusCode);
      res.type(Constants.APPLICATION_JSON);

      return returnValue;
    });

    // PUT = correct
    String correctRoute = "/" + Constants.BLOODPRESSURE_PATH + ":id";

    put(correctRoute, (req, res) -> {
      String body = req.body();
      String uniqueId = req.params(":id");

      TeleObservation teleObs = gson.fromJson(body, TeleObservation.class);

      boolean isValid = teleMed.correct(uniqueId, teleObs);

      int lastStatusCode = HttpServletResponse.SC_OK;
      if (!isValid) {
        lastStatusCode = HttpServletResponse.SC_NOT_FOUND;
      }

      // Normally: 200 OK
      res.status(lastStatusCode);
      res.type(Constants.APPLICATION_JSON);

      return "{}";
    });

    // DELETE = delete
    String deleteRoute = "/" + Constants.BLOODPRESSURE_PATH + ":id";
    delete(deleteRoute, (req, res) -> {
      String uniqueId = req.params(":id");

      boolean isValid = teleMed.delete(uniqueId);

      int lastStatusCode = HttpServletResponse.SC_NO_CONTENT;
      if (!isValid) {
        lastStatusCode = HttpServletResponse.SC_NOT_FOUND;
      }

      res.status(lastStatusCode);
      res.type(Constants.APPLICATION_JSON);

      return "{}";
    });

    // GET HTML = getObservationFor
    String getHtmlRoute = "/bloodpressure/:patientId";

    get(getHtmlRoute, (req, res) -> {
      String patientId = req.params(":patientId");

      List<TeleObservation> theList = teleMed.getObservationsFor(patientId,
          TimeInterval.LAST_DAY);

      res.status(HttpServletResponse.SC_OK);

      String returnValue = "<h1>Observations for " + patientId + "</h1>\n";
      returnValue += "<ol>";
      for (TeleObservation to : theList) {
        returnValue += "<li>" + to.toString() + "</li>";
      }
      returnValue += "</ol>";

      return returnValue;
    });

  }

  public void closedown() {
    stop();
  }
}
