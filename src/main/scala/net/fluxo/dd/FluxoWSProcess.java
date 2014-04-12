package net.fluxo.dd;

import net.fluxo.dd.dbo.Task;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

/**
 * User: Ronald Kurniawan (viper)
 * Date: 11/04/14 2:36PM
 * Comment:
 */
@Path("/ws")
public class FluxoWSProcess {

	@GET
	@Path("/ping")
	@Produces("text/plain")
	public Response test() {
		return Response.status(200).entity("FLUXO-REST-WS").build();
	}

	@GET
	@Path("/ylist/page/{page}/quality/{quality}/rating/{rating}")
	@Produces("application/json")
	public Response getYIFYList(@PathParam("page") int page, @PathParam("quality") int quality, @PathParam("rating") int rating) {
		String response = YIFYP.procListMovie(page, quality,rating, OUtils.ExternalIP(), OUtils.readConfig().HTTPDPort());
		return Response.status(200).entity(response).build();
	}

	@GET
	@Path("/ydetails/{id}")
	@Produces("application/json")
	public Response getYIFYMovieDetails(@PathParam("id") int id) {
		String response = YIFYP.procMovieDetails(id, OUtils.ExternalIP(), OUtils.readConfig().HTTPDPort());
		return Response.status(200).entity(response).build();
	}

	@GET
	@Path("/ysearch/{st}")
	@Produces("application/json")
	public Response getYIFYSearchResult(@DefaultValue("") @QueryParam("st") String search) {
		String response = YIFYP.procYIFYSearch(search);
		return Response.status(200).entity(response).build();
	}

	@GET
	@Path("/status/{id}")
	@Produces("text/plain")
	public Response getDownloadStatus(@PathParam("id") String userID) {
		Task[] arrTasks = DbControl.queryTasks(userID);
		StringBuilder sb = new StringBuilder();
		for (Task t : arrTasks) {
			int progress = -1;
			if (t.TaskCompletedLength() > 0 && t.TaskTotalLength() > 0) {
				progress = (int)((t.TaskCompletedLength() * 100) / t.TaskTotalLength());
			}
			String dlName = "Unknown Download";
			if (t.TaskPackage().nonEmpty()) {
				dlName = t.TaskPackage().get();
			}
			sb.append(dlName).append(" --> ").append(progress).append("%").append(System.lineSeparator());
		}
		if (arrTasks.length == 0) {
			sb.append("No active tasks are running!");
		}
		return Response.status(200).entity(sb.toString()).build();
	}
}
