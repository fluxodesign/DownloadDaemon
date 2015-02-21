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
import net.fluxo.plugins.kas.TrKas;
import net.fluxo.plugins.tpb.TrTPB;
import net.xeoh.plugins.base.PluginManager;
import org.apache.commons.codec.net.URLCodec;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;

/**
 * This class contains REST methods that are served via the embedded Jetty server.
 * <p>Each of these methods has one counterpart that can be called via an XMPP message.</p>
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 11/04/14
 */
@Path("/ws")
public class FluxoWSProcess {

	/**
	 * This method should return a HTTP/200 and a string ("FLUXO-REST-WS") when called.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ping</p>
	 *
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
	 *
	 * @param page    page number to serve (starts from 1)
	 * @param quality movie quality to display (0: all movies; 1: 720p movies only; 2: 1080p movies only; 3: 3D movies only)
	 * @param rating  IMDB rating to filter the list; starts from 0, which displays all movies all through to 9
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object of movie list
	 */
	@GET
	@Path("/ylist/page/{page}/quality/{quality}/rating/{rating}")
	@Produces("application/json")
	public Response getYIFYList(@PathParam("page") int page, @PathParam("quality") int quality, @PathParam("rating") int rating) {
		try {
			String response = YIFYP.procListMovie(page, quality, rating, OUtils.ExternalIP(), OUtils.readConfig().HTTPPort());
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			System.out.println("--> ERROR: " + e.getMessage());
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	/**
	 * Return a JSON object containing the details of a particular movie.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ydetails/[movie-id]</p>
	 *
	 * @param id the id of the movie
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object of the movie details
	 */
	@GET
	@Path("/ydetails/{id}")
	@Produces("application/json")
	public Response getYIFYMovieDetails(@PathParam("id") int id) {
		try {
			String response = YIFYP.procMovieDetails(id, OUtils.ExternalIP(), OUtils.readConfig().HTTPPort());
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	/**
	 * Return a JSON object containing the results of a search on YIFY site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/ysearch?st=[search-term]</p>
	 *
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
	 *
	 * @param userID the user ID to query
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with list of current downloads for
	 * a particular user
	 */
	@GET
	@Path("/status/{id}")
	@Produces("application/json")
	public Response getDownloadStatus(@Context HttpServletRequest htRequest, @PathParam("id") String userID) {
		String username = htRequest.getHeader("DDUSER");
		String password = htRequest.getHeader("DDPWD");
		if (username == null || password == null || !DbControl.authCredentials(username, password)) {
			return Response.status(400).entity("NOT-AUTHORIZED").build();
		}
		try {
			Task[] arrTasks = DbControl.queryTasks(userID);
			HashMap<String, String> progressMap = new HashMap<>();
			for (Task t : arrTasks) {
				int progress = -1;
				if (t.TaskCompletedLength() > 0 && t.TaskTotalLength() > 0) {
					progress = (int) ((t.TaskCompletedLength() * 100) / t.TaskTotalLength());
				}
				String dlName = "Unknown Download";
				if (t.TaskPackage().nonEmpty()) {
					dlName = t.TaskPackage().get();
				}
				progressMap.put(dlName, String.valueOf(progress));
			}
			String response = OUtils.DownloadProgressToJson(progressMap);
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	/**
	 * Add a bittorrent URL to current list of downloads for the server to process.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/addtorrent/[user-id]/[torrent-url]</p>
	 *
	 * @param htRequest  a HttpServletRequest object
	 * @param uri   bittorrent magnet url or http torrent url to download
	 * @param owner user ID associated with this download
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
	@GET
	@Path("/addtorrent/{owner}/{uri}")
	@Produces("text/plain")
	public Response getTorrentUrl(@Context HttpServletRequest htRequest, @DefaultValue("") @PathParam("uri") String uri,
	                              @DefaultValue("") @PathParam("owner") String owner) {
		String username = htRequest.getHeader("DDUSER");
		String password = htRequest.getHeader("DDPWD");
		if (username == null || password == null || !DbControl.authCredentials(username, password)) {
			return Response.status(400).entity("NOT-AUTHORIZED").build();
		}
		try {
			if (uri.length() > 0 && owner.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				String response = OAria.processRequest(decodedURL, owner, false, "", "");
				// DEBUG
				System.out.println("---> ADD_TORRENT RESPONSE: " + response);
				return Response.status(200).entity(response).build();
			}
		} catch (UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER").build();
	}

	/**
	 * Add a video site URL to current list of downloads for the server to process.
	 * @param htRequest a HttpServletRequest object
	 * @param uri video URL from a supported video sharing website
	 * @param owner user ID associated with this download
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
	@GET
	@Path("/addvid/{owner}/{uri}")
	@Produces("text/plain")
	public Response addNewVideoDownload(@Context HttpServletRequest htRequest, @DefaultValue("") @PathParam("uri") String uri,
	                                    @DefaultValue("") @PathParam("owner") String owner) {
		String username = htRequest.getHeader("DDUSER");
		String password = htRequest.getHeader("DDPWD");
		if (username == null || password == null || !DbControl.authCredentials(username, password)) {
			return Response.status(400).entity("NOT-AUTHORIZED").build();
		}
		try {
			if (uri.length() > 0 && owner.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				String response = OVideoP.processRequest(decodedURL, owner);
				return Response.status(200).entity(response).build();
			}
		} catch (UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER").build();
	}

	/**
	 * Add a HTTP-based download to current list of downloads for the server to process.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/adduri/[user-id]/[http-url]</p>
	 *
	 * @param uri   HTTP download url
	 * @param owner user ID associated with this download
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
	@GET
	@Path("/adduri/{owner}/{uri}")
	@Produces("text/plain")
	public Response getHttpUrl(@Context HttpServletRequest htRequest, @DefaultValue("") @PathParam("uri") String uri,
	                           @DefaultValue("") @PathParam("owner") String owner) {
		String username = htRequest.getHeader("DDUSER");
		String password = htRequest.getHeader("DDPWD");
		if (username == null || password == null || !DbControl.authCredentials(username, password)) {
			return Response.status(400).entity("NOT-AUTHORIZED").build();
		}
		try {
			if (uri.length() > 0 && owner.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				String response = OAria.processRequest(decodedURL, owner, true, "", "");
				return Response.status(200).entity(response).build();
			}
		} catch (UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER").build();
	}

	/**
	 * Add a HTTP-based download to current list of downloads for the server to process, with authentication (username
	 * and password).
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/adduric/[user-id]/[username]/[password]/[http-url]</p>
	 *
	 * @param uri      HTTP download url
	 * @param owner    user ID associated with this download
	 * @param username username for authentication
	 * @param password password for authentication
	 * @return a string containing the status of the request; "OK" followed by download ID or an error message
	 */
	@GET
	@Path("/adduric/{owner}/{username}/{password}/{uri}")
	@Produces("text/plain")
	public Response getHttpUrlC(@Context HttpServletRequest htRequest, @DefaultValue("") @PathParam("uri") String uri,
	                            @DefaultValue("") @PathParam("owner") String owner, @DefaultValue("") @PathParam("username") String username,
	                            @DefaultValue("") @PathParam("password") String password) {
		String credUsername = htRequest.getHeader("DDUSER");
		String credPassword = htRequest.getHeader("DDPWD");
		if (credUsername == null || credPassword == null || !DbControl.authCredentials(credUsername, credPassword)) {
			return Response.status(400).entity("NOT-AUTHORIZED").build();
		}
		try {
			if (uri.length() > 0 && owner.length() > 0 && username.length() > 0 && password.length() > 0) {
				String decodedURL = URLDecoder.decode(uri, "UTF-8");
				String response = OAria.processRequest(decodedURL, owner, true, username, password);
				return Response.status(200).entity(response).build();
			}
		} catch (UnsupportedEncodingException uee) {
			return Response.status(500).entity(uee.getMessage()).build();
		}
		return Response.status(400).entity("EITHER-URI-ERROR-OR-NO-OWNER-OR-USERNAME-PASSWORD-ERROR").build();
	}

	/**
	 * Return a JSON object containing the list of search results from a certain notorius torrent site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/tpb/[search-term]/[page]/[categories]</p>
	 *
	 * @param searchTerm the search term
	 * @param page       page number to page number to serve (starts from 0)
	 * @param cats       list of category number, separated by commas
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with search results from a notorious
	 * torrents site
	 */
	@GET
	@Path("/tpb/{st}/{page}/{cat}")
	@Produces("application/json")
	public Response getTPBSearchResult(@DefaultValue("") @PathParam("st") String searchTerm, @DefaultValue("0") @PathParam("page") int page,
	                                   @DefaultValue("0") @PathParam("cat") String cats) {
		PluginManager pm = OPlugin.getPluginManager();
		TrTPB trTPB = pm.getPlugin(TrTPB.class);
		if (trTPB == null) {
			return Response.status(500).entity("Plugin not found!").build();
		}
		try {
			// Build the String array of "chat" commands...
			String[] arrTerm = new String[5];
			arrTerm[0] = "DD";
			arrTerm[1] = "TPB";
			arrTerm[2] = "ST=\"" + searchTerm + "\"";
			arrTerm[3] = "PG=" + page;
			arrTerm[4] = "CAT=" + cats;
			String response = trTPB.process(arrTerm); //TPBP.query(decodedTerm, page, arrCats);
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	/**
	 * Return the description of a particular torrent object from a certain notorious torrent site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/tpbdetails/[url-to-torrent]</p>
	 *
	 * @param url the url a particular torrent
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with details of a particular torrent
	 * from a notorious torrent site
	 */
	@GET
	@Path("/tpbdetails/{url}")
	@Produces("application/json")
	public Response getTPBDetails(@DefaultValue("") @PathParam("url") String url) {
		PluginManager pm = OPlugin.getPluginManager();
		TrTPB trTPB = pm.getPlugin(TrTPB.class);
		if (trTPB == null) {
			return Response.status(500).entity("Plugin not found!").build();
		}
		try {
			if (url.length() > 0) {
				String[] arrTerm = new String[3];
				arrTerm[0] = "DD";
				arrTerm[1] = "TPBDETAILS";
				arrTerm[2] = url;
				String decodedURL = URLDecoder.decode(url, "UTF-8");
				if (decodedURL.startsWith("http://thepiratebay.se/")) {
					String response = trTPB.process(arrTerm); //TPBP.queryDetails(FilenameUtils.getPath(decodedURL));
					return Response.status(200).entity(response).build();
				}
			}
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
		return Response.status(400).entity("Unable to process TPB Details request").build();
	}

	/**
	 * Return a JSON object containing the list of search results from a certain notorius torrent site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/kas/[search-term]/[page]/[categories]</p>
	 *
	 * @param searchTerm the search term
	 * @param page       page number to page number to serve (starts from 0)
	 * @param cat       list of category number, separated by commas
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with search results from a notorious
	 * torrents site
	 */
	@GET
	@Path("/kas/{st}/{page}/{cat}")
	@Produces("application/json")
	public Response getKickAssSearchResult(@DefaultValue("") @PathParam("st") String searchTerm, @DefaultValue("0") @PathParam("page") int page,
	                                       @DefaultValue("0") @PathParam("cat") String cat) {
		PluginManager pm = OPlugin.getPluginManager();
		TrKas trKas = pm.getPlugin(TrKas.class);
		if (trKas == null) {
			return Response.status(500).entity("Plugin not found!").build();
		}
		try {
			// Build the String array of "chat" commands...
			String[] arrTerm = new String[5];
			arrTerm[0] = "DD";
			arrTerm[1] = "KAST";
			arrTerm[2] = "ST=\"" + searchTerm + "\"";
			arrTerm[3] = "PG=" + page;
			arrTerm[4] = "CAT=" + cat;
			String response = trKas.process(arrTerm);
			return Response.status(200).entity(response).build();
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
	}

	/**
	 * Return the description of a particular torrent object from a certain notorious torrent site.
	 * <p>URL to reach this method: http://[address-or-ip]:[port]/comm/rs/ws/kasdetails/[url-to-torrent]</p>
	 *
	 * @param url the url a particular torrent
	 * @return a {@link javax.ws.rs.core.Response} object containing a JSON object with details of a particular torrent
	 * from a notorious torrent site
	 */
	@GET
	@Path("/kasdetails/{url}")
	@Produces("application/json")
	public Response getKickAssDetails(@DefaultValue("") @PathParam("url") String url) {
		PluginManager pm = OPlugin.getPluginManager();
		TrKas trKas = pm.getPlugin(TrKas.class);
		if (trKas == null) {
			return Response.status(500).entity("Plugin not found!").build();
		}
		try {
			if (url.length() > 0) {
				String[] arrTerm = new String[3];
				arrTerm[0] = "DD";
				arrTerm[1] = "KASTDETAILS";
				arrTerm[2] = url;
				String decodedURL = URLDecoder.decode(url, "UTF-8");
				if (decodedURL.startsWith("https://kickass.to/")) {
					String response = trKas.process(arrTerm);
					return Response.status(200).entity(response).build();
				}
			}
		} catch (Exception e) {
			return Response.status(400).entity(e.getMessage()).build();
		}
		return Response.status(400).entity("Unable to process KAST Details request").build();
	}

}
