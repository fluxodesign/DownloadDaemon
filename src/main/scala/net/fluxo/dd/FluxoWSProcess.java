/*
 * FluxoWSProcess.java
 *
 * Copyright (c) 2014 Ronald Kurniawan. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.fluxo.dd;

import net.fluxo.dd.dbo.Task;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.io.FilenameUtils;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * This class contains REST methods that are served via the embedded Jetty server.
 * <p>Each of these methods has one counterpart that can be called via an XMPP message.</p>
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 11/04/14
 */
@Path("/ws")
public class FluxoWSProcess {

	/**
	 * This method should return a HTTP/200 and a string ("FLUXO-REST-WS") when called.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ping</p>
	 * @return a {@link javax.ws.rs.core.Response} object signifying that the server is alive and responding.
	 */
	@GET
	@Path("/ping")
	@Produces("text/plain")
	public Response test() {
		return Response.status(200).entity("FLUXO-REST-WS").build();
	}

	/**
	 * Return a JSON object containing the list of movies available on YIFY site, newest to oldest.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ylist/page/[page]/quality/[quality]/rating/[rating]</p>
	 * @param page page number to serve (starts from 1)
	 * @param quality movie quality to display (0: all movies; 1: 720p movies only; 2: 1080p movies only; 3: 3D movies only)
	 * @param rating IMDB rating to filter the list; starts from 0, which displays all movies all through to 9
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object of movie list
	 */
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

	/**
	 * Return a JSON object containing the details of a particular movie.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ydetails/[movie-id]</p>
	 * @param id the id of the movie
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object of the movie details
	 */
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

	/**
	 * Return a JSON object containing the results of a search on YIFY site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ysearch?st=[search-term]</p>
	 * @param search the search term, could be title or part of title of the movie(s)
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object of the search results
	 */
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

	/**
	 * Returns the status of the downloads for a particular user.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/status/[user-id]</p>
	 * @param userID the user ID to query
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with list of current downloads for
	 * a particular user
	 */
	@GET
	@Path("/status/{id}")
	@Produces("application/json")
	public Response getDownloadStatus(@PathParam("id") String userID) {
		try {
			Task[] arrTasks = DbControl.queryTasks(userID);
			//StringBuilder sb = new StringBuilder();
			HashMap<String,String> progressMap = new HashMap<>();
			for (Task t : arrTasks) {
				int progress = -1;
				if (t.TaskCompletedLength() > 0 && t.TaskTotalLength() > 0) {
					progress = (int) ((t.TaskCompletedLength() * 100) / t.TaskTotalLength());
				}
				String dlName = "Unknown Download";
				if (t.TaskPackage().nonEmpty()) {
					dlName = t.TaskPackage().get();
				}
				//sb.append(dlName).append(" --> ").append(progress).append("%").append(System.lineSeparator());
				progressMap.put(dlName, String.valueOf(progress));
			}
			/*if (arrTasks.length == 0) {
				sb.append("No active tasks are running!");
			}*/
			String response = OUtils.DownloadProgressToJson(progressMap);
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	/**
	 * Add a bittorrent URL to current list of downloads for the server to process.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/addtorrent/[user-id]/[torrent-url]</p>
	 * @param uri bittorrent magnet url or http torrent url to download
	 * @param owner user ID associated with this download
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
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

	/**
	 * Add a HTTP-based download to current list of downloads for the server to process.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/adduri/[user-id]/[http-url]</p>
	 * @param uri HTTP download url
	 * @param owner user ID associated with this download
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
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

	/**
	 * Add a HTTP-based download to current list of downloads for the server to process, with authentication (username
	 * and password).
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/adduric/[user-id]/[username]/[password]/[http-url]</p>
	 * @param uri HTTP download url
	 * @param owner user ID associated with this download
	 * @param username username for authentication
	 * @param password password for authentication
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
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

	/**
	 * Return a JSON object containing the list of search results from a certain notorius torrent site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/tpb/[search-term]/[page]/[categories]</p>
	 * @param searchTerm the search term
	 * @param page page number to page number to serve (starts from 0)
	 * @param cats list of category number, separated by commas
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with search results from a notorious
	 * torrents site
	 */
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

	/**
	 * Return the description of a particular torrent object from a certain notorious torrent site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/tpbdetails/[url-to-torrent]</p>
	 * @param url the url a particular torrent
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with details of a particular torrent
	 * from a notorious torrent site
	 */
	@GET
	@Path("/tpbdetails/{url}")
	@Produces("application/json")
	public Response getTPBDetails(@DefaultValue("") @PathParam("url") String url) {
		try {
			if (url.length() > 0) {
				String decodedURL = URLDecoder.decode(url, "UTF-8");
				if (decodedURL.startsWith("http://thepiratebay.se/")) {
					String response = TPBP.queryDetails(FilenameUtils.getPath(decodedURL));
					return Response.status(200).entity(response).build();
				}
			}
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
		return Response.status(400).entity("Unable to process TPB Details request").build();
	}
}
