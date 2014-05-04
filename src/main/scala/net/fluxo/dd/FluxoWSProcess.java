package net.fluxo.dd;

import net.fluxo.dd.dbo.Task;
import org.apache.commons.codec.net.URLCodec;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.StringTokenizer;

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
		try {
			String response = YIFYP.procListMovie(page, quality, rating, OUtils.ExternalIP(), OUtils.readConfig().HTTPDPort());
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	@GET
	@Path("/ydetails/{id}")
	@Produces("application/json")
	public Response getYIFYMovieDetails(@PathParam("id") int id) {
		try {
			String response = YIFYP.procMovieDetails(id, OUtils.ExternalIP(), OUtils.readConfig().HTTPDPort());
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	@GET
	@Path("/ysearch")
	@Produces("application/json")
	public Response getYIFYSearchResult(@DefaultValue("") @QueryParam("st") String search) {
		try {
			if (search.length() > 0) {
				String decodedTerm = (new URLCodec()).decode(search);
				String response = YIFYP.procYIFYSearch(decodedTerm);
				return Response.status(200).entity(response).build();
			}
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
		return Response.status(400).entity("NO-SEARCH-TERM").build();
	}

	@GET
	@Path("/status/{id}")
	@Produces("text/plain")
	public Response getDownloadStatus(@PathParam("id") String userID) {
		try {
			Task[] arrTasks = DbControl.queryTasks(userID);
			StringBuilder sb = new StringBuilder();
			for (Task t : arrTasks) {
				int progress = -1;
				if (t.TaskCompletedLength() > 0 && t.TaskTotalLength() > 0) {
					progress = (int) ((t.TaskCompletedLength() * 100) / t.TaskTotalLength());
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
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	@GET
	@Path("/addtorrent/{owner}/{uri}")
	@Produces("text/plain")
	public Response getTorrentUrl(@DefaultValue("") @PathParam("uri") String uri, @DefaultValue("") @PathParam("owner") String owner) {
		try {
			if (uri.length() > 0 && owner.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				String response = OAria.processRequest(decodedURL, owner, false, "", "");
				return Response.status(200).entity(response).build();
			}
		} catch(UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER").build();
	}

	@GET
	@Path("/adduri/{owner}/{uri}")
	@Produces("text/plain")
	public Response getHttpUrl(@DefaultValue("") @PathParam("uri") String uri, @DefaultValue("") @PathParam("owner") String owner) {
		try {
			if (uri.length() > 0 && owner.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				//String decodedUri = (new URLCodec()).decode(uri);
				String response = OAria.processRequest(decodedURL, owner, true, "", "");
				return Response.status(200).entity(response).build();
			}
		} catch(UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER").build();
	}

	@GET
	@Path("/adduric/{owner}/{username}/{password}/{uri}")
	@Produces("text/plain")
	public Response getHttpUrlC(@DefaultValue("") @PathParam("uri") String uri, @DefaultValue("") @PathParam("owner") String owner,
		@DefaultValue("") @PathParam("username") String username, @DefaultValue("") @PathParam("password") String password) {
		try {
			if (uri.length() > 0 && owner.length() > 0 && username.length() > 0 && password.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				String response = OAria.processRequest(decodedURL, owner, true, username, password);
				return Response.status(200).entity(response).build();
			}
		} catch(UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER-OR-USERNAME-PASSWORD-ERROR").build();
	}

	@GET
	@Path("/tpb/{st}/{page}/{cat}")
	@Produces("application/json")
	public Response getTPBSearchResult(@DefaultValue("") @PathParam("st") String searchTerm, @DefaultValue("0") @PathParam("page") int page,
	    @DefaultValue("0") @PathParam("cat") String cats) {
		try {
			if (searchTerm.length() > 0) {
				URLCodec ucodec = new URLCodec();
				String decodedTerm = ucodec.decode(searchTerm);
				int[] arrCats;
				if (cats.length() > 0 && cats.contains(",")) {
					StringTokenizer tokenizer = new StringTokenizer(cats, ",");
					arrCats = new int[tokenizer.countTokens()];
					int arrIndex = 0;
					while (tokenizer.hasMoreTokens()) {
						String t = tokenizer.nextToken();
						arrCats[arrIndex] = Integer.parseInt(t);
						arrIndex++;
					}
				} else {
					arrCats = new int[]{ Integer.parseInt(cats) };
				}
				String response = TPBP.query(decodedTerm, page, arrCats);
				return Response.status(200).entity(response).build();
			}
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
		return Response.status(400).entity("Unable to process TPB request").build();
	}

	@GET
	@Path("/tpbdetails/{url}")
	@Produces("application/json")
	public Response getTPBDetails(@DefaultValue("") @PathParam("url") String url) {
		try {
			if (url.length() > 0) {
				String decodedURL = OUtils.decrypt(url);
				if (decodedURL.startsWith("http://thepiratebay.se/")) {
					String response = TPBP.queryDetails(decodedURL);
					return Response.status(200).entity(response).build();
				}
			}
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
		return Response.status(400).entity("Unable to process TPB Details request").build();
	}
}
