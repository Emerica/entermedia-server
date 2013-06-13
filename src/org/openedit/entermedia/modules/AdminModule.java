/*
Copyright (c) 2003 eInnovation Inc. All rights reserved

This library is free software; you can redistribute it and/or modify it under the terms
of the GNU Lesser General Public License as published by the Free Software Foundation;
either version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU Lesser General Public License for more details.
 */

package org.openedit.entermedia.modules;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.data.SearcherManager;
import org.openedit.users.AllowViewing;
import org.openedit.users.PasswordHelper;
import org.openedit.users.UserSearcher;

import com.openedit.OpenEditException;
import com.openedit.WebPageRequest;
import com.openedit.config.Configuration;
import com.openedit.modules.BaseModule;
import com.openedit.page.Page;
import com.openedit.page.PageRequestKeys;
import com.openedit.page.PageStreamer;
import com.openedit.page.Permission;
import com.openedit.page.PermissionManager;
import com.openedit.page.manage.PageManager;
import com.openedit.users.Group;
import com.openedit.users.User;
import com.openedit.users.authenticate.AuthenticationRequest;
import com.openedit.users.authenticate.PasswordGenerator;
import com.openedit.util.PathUtilities;
import com.openedit.util.StringEncryption;
import com.openedit.util.URLUtilities;

/**
 * This module allows the user to view and administer the site.
 * 
 * @author Eric Galluzzo
 * @author Matt Avery, mavery@einnovation.com
 */
public class AdminModule extends BaseModule
{
	protected static final String ENTERMEDIAKEY = "entermedia.key";  //username + md542 + md5password + tstamp + timestampenc
	protected static final String TIMESTAMP = "tstamp";
	protected static final long MILLISECONDS_PER_DAY = 24*60*60*1000;// milliseconds in one day (used to calculate password expiry)
	
	protected String fieldImagesRoot; // used by the imagepicker
	protected String fieldRootFTPURL;
	protected static final String UNAME = "username";
	protected static final String EMAIL = "to";
	private static final Log log = LogFactory.getLog(AdminModule.class);
	protected StringEncryption fieldCookieEncryption;
	protected SendMailModule sendMailModule;
	protected List fieldWelcomeFiles;
	
	public List getWelcomeFiles()
	{
		return fieldWelcomeFiles;
	}

	public void setWelcomeFiles(List inWelcomeFiles)
	{
		fieldWelcomeFiles = inWelcomeFiles;
	}
	public AdminModule()
	{
		super();
	}

	public SearcherManager getSearcherManager()
	{
		return (SearcherManager) getBeanFactory().getBean("searcherManager");
	}

	/**
	 * Sets the root FTP URL.
	 * 
	 * @param rootFTPURL
	 *            The root FTP URL to set
	 */
	public void setRootFTPURL(String rootFTPURL)
	{
		if ((rootFTPURL != null) && rootFTPURL.endsWith("/"))
		{
			fieldRootFTPURL = rootFTPURL.substring(0, rootFTPURL.length() - 1);
		}
		else
		{
			fieldRootFTPURL = rootFTPURL;
		}
	}

	/**
	 * Returns the root FTP URL.
	 * 
	 * @return A string, which will not end in a slash, or <code>null</code> if
	 *         FTP support has not been configured.
	 */
	public String getRootFTPURL()
	{
		return fieldRootFTPURL;
	}
	//TODO: Use Spring
	protected PasswordHelper getPasswordHelper(WebPageRequest inReq) throws OpenEditException
	{
		PasswordHelper passwordHelper = (PasswordHelper) inReq.getSessionValue("passwordHelper");
		if (passwordHelper == null)
		{
			passwordHelper = new PasswordHelper();
			passwordHelper.setSendMailModule(sendMailModule);
			inReq.putSessionValue("passwordHelper", passwordHelper);
		}

		return passwordHelper;
	}

	public void emailPasswordReminder(WebPageRequest inReq) throws Exception
	{
		String e = inReq.getRequestParameter(EMAIL);
		String u = inReq.getRequestParameter(UNAME);
		if (e == null && u == null)
		{
			inReq.putPageValue("commandSucceeded", "didnotexecute");
			// log.error("Invalid information");
			return;
		}

		User foundUser = null;
		String username = null;
		String email = null;
		String password = null;
		// if the user provided an email instead of a username, lookup username
		String emailaddress = inReq.getRequestParameter(EMAIL);
		if (emailaddress != null && emailaddress.length() > 0)
		{
			foundUser = (User) getUserSearcher().getUserByEmail(emailaddress);
		}
		if( foundUser == null )
		{
			// If the user provided a valid username
			username = inReq.getRequestParameter(UNAME);
			if (username != null )
			{
				foundUser = (User) getUserSearcher().getUser(username);
			}
		}
		if (foundUser != null)
		{
			email = foundUser.getEmail();
			if (email == null || email.equals(""))
			{
				inReq.putPageValue("error", "noemail");
				return;
			}
			// get the user's current password
			if (foundUser.getPassword().startsWith("DES:"))
			{
				password = getUserManager().getStringEncryption().decrypt(foundUser.getPassword());
			}
			else
			{
				password = foundUser.getPassword();
			}
			username = foundUser.getUserName();
		}
		else
		{
			inReq.putPageValue("error", "nouser");
			inReq.putPageValue("commandSucceeded", "nouser");
			return;
		}
		inReq.putPageValue("founduser", foundUser);

		// let the passwordHelper send the password
		PasswordHelper passwordHelper = getPasswordHelper(inReq);

		String passenc = getUserManager().getStringEncryption().getPasswordMd5(foundUser.getPassword());
		passenc = foundUser.getUserName() + "md542" + passenc;
		
		//append an encrypted timestamp to passenc
		try{
			
			String expiry = inReq.getPageProperty("temporary_password_expiry");
			if (expiry == null || expiry.isEmpty())
			{
				log.info("Temporary password expiry is not enabled.");
			}
			else
			{
				int days = 0;
				try
				{
					days = Integer.parseInt(expiry);
				}catch (Exception ee){}
				if (days <=0)
				{
					log.info("Temporary password expiry is not formatted correctly - require a number greater than 0.");
				}
				else
				{
					String tsenc = getUserManager().getStringEncryption().encrypt(String.valueOf(new Date().getTime()));
					if (tsenc!=null && !tsenc.isEmpty()) {
						if (tsenc.startsWith("DES:")) tsenc = tsenc.substring("DES:".length());//kloog: remove DES: prefix since appended to URL
						passenc += TIMESTAMP + tsenc;
					} else{
						log.info("Unable to append encrypted timestamp. Autologin URL does not have an expiry.");
					}
				}
			}
		}catch (OpenEditException oex){
			log.error(oex.getMessage(), oex);
			log.info("Unable to append encrypted timestamp. Autologin URL does not have an expiry.");
		}
		passwordHelper.emailPasswordReminder(inReq, getPageManager(), username, password, passenc, email);

	}
	public void loadPermissions(WebPageRequest inReq) throws Exception
	{
		String catid = inReq.findValue("catalogid");
		if( catid == null )
		{
			catid = "system";
		}
		PermissionManager manager = (PermissionManager)getModuleManager().getBean(catid, "permissionManager"); 
		String limited = inReq.getCurrentAction().getChildValue("permissions");
		manager.loadPermissions(inReq, inReq.getContentPage(), limited);
	}


	//We will see if we use this or not. Actions may want to handle it themself
	public void permissionRedirect(WebPageRequest inReq) throws OpenEditException
	{
		String name = inReq.findValue("permission");
		String value = (String) inReq.getPageValue("can" + name);
		if (!Boolean.parseBoolean(value))
		{
			String login = inReq.findValue("redirectpath");
			if (login != null)
			{
				inReq.redirect(login);
			}
		}
	}

	/**
	 * 
	 * @deprecated Use Admin.loadPermissions then check for the "canedit" page
	 *             property
	 * @throws Exception
	 */

	public void allowEditing(WebPageRequest inReq) throws Exception
	{
		//		if( inReq.getPageValue("canedit") == null)
		//		{
		boolean value = false;
		if (inReq.getUser() != null)
		{
			Permission filter = inReq.getPage().getPermission("edit");
			value = ((filter == null) || filter.passes(inReq));
		}
		inReq.setEditable(value);
		//		}
	}

	public void allowViewing(WebPageRequest inReq) throws OpenEditException
	{
		AllowViewing command = new AllowViewing();
		command.setPageManager(getPageManager());
		command.configure(inReq);
		command.execute(inReq);
	}

	public void checkForDuplicateByEmail(WebPageRequest inReq) throws Exception
	{
		String email = inReq.getRequiredParameter("email");

		User user = getUserSearcher().getUserByEmail(email);
		if (user != null)
		{
			String page = inReq.getCurrentAction().getConfig().getChildValue("redirectpage");
			if (page == null)
			{
				inReq.redirect(page);
			}
			else
			{
				inReq.putPageValue("oe-exception", "Account already exists with address " + email);
			}
		}
	}

	protected UserSearcher getUserSearcher()
	{
		return (UserSearcher) getSearcherManager().getSearcher("system", "user");
	}

	/*
	 * public void loginByEmail( WebPageRequest inReq ) throws Exception {
	 * String account = inReq.getRequestParameter("email");
	 * 
	 * if ( account != null ) { User user = getUserSearcher().getUserByEmail(
	 * account ); loginAndRedirect(user,inReq); } else { String referrer =
	 * inReq.getRequest().getHeader("REFERER"); if ( referrer != null ) { //this
	 * is the original page someone might have been on
	 * inReq.putSessionValue("originalEntryPage",referrer ); } } }
	 */
	public void login(WebPageRequest inReq) throws Exception
	{
		String account = inReq.getRequestParameter("accountname");
		String password = inReq.getRequestParameter("password");

		if (account == null && inReq.getRequest() != null && inReq.getSessionValue("fullOriginalEntryPage") == null)
		{
			String referrer = inReq.getRequest().getHeader("REFERER"); 
			if ( referrer != null &&  !referrer.contains("authentication") && referrer.startsWith( inReq.getSiteRoot()) ) 
				
			{ //the original page someone might have been on
				inReq.putSessionValue("fullOriginalEntryPage",referrer );  
			}			
		}
		else if( account != null)
		{
			User user = getUserSearcher().getUser(account);
			if (user == null && account.contains("@"))
			{
				user = getUserSearcher().getUserByEmail(account);
			}
			if (user == null) // Allow guest user
			{
				String groupname = inReq.getPage().get("autologingroup");
				if (groupname != null)
				{
					//we dont want to save the real password since it might be NT based
					String tmppassword = new PasswordGenerator().generate();
					user = getUserManager().createGuestUser(account, tmppassword, groupname);
					log.info("Username not found. Creating guest user.");
				}
			}
			if (password == null)
			{
				inReq.putPageValue("oe-exception", "Password cannot be blank");
				return;
			}
			if (user == null)
			{
				inReq.putPageValue("oe-exception", "Invalid Logon");
				return;
			}
			AuthenticationRequest aReq = new AuthenticationRequest();
			aReq.setUser(user);
			aReq.setPassword(password);

			String domain = inReq.getRequestParameter("domain");
			if (domain == null)
			{
				domain = inReq.getContentPage().get("authenticationdomain");
			}
			aReq.putProperty("authenticationdomain", domain);
			String server = inReq.getPage().get("authenticationserver");
			aReq.putProperty("authenticationserver", server);

			if (loginAndRedirect(aReq, inReq))
			{
				user.setVirtual(false);
				getUserManager().saveUser(user);
			}
		}
	}

	/**
	 * @param inUser
	 * @param inReq
	 */
	protected boolean loginAndRedirect(AuthenticationRequest inAReq, WebPageRequest inReq) throws Exception
	{
		User inUser = inAReq.getUser();
		boolean userok = false;
		String sendTo = inReq.getRequestParameter("loginokpage");
		String maxcounts = inReq.findValue("maxfailedloginattemps");
		int maxattemps = 5;
		if(maxcounts != null){
			try{
				maxattemps = Integer.parseInt(maxcounts);
			} catch(Exception e){
				
			}
		}
		boolean disable = Boolean.parseBoolean(inReq.getContentProperty("autodisableusers"));
		if (inUser != null)
		{
			// Save our logged-in user in the session,
			// because we use it again later.
			if (inAReq.getPassword() != null || inUser.getPassword() != null)
			{
				if (inUser.isEnabled())
				{
					userok = getUserManager().authenticate(inAReq); //<---- This is it!!!! we login
				}
				else
				{
					inReq.putSessionValue("oe-exception", "User has been disabled");
					inReq.putPageValue("oe-exception", "User has been disabled");
					inReq.putPageValue("disabled", true);
					inReq.putPageValue("invaliduser", inUser);
					return false;
				}
			}
		}

		if (userok)
		{
			if (disable)
			{
				//This resets the "failed attemps" to 0.
				inUser.setProperty("failedlogincount", "0");
				getUserManager().saveUser(inUser);
				
			}
			
//			// check validation
//			String lastTime= inUser.getLastLoginTime(); 
//			if(lastTime != null){
//				int duration= Integer.parseInt(inReq.getPageProperty("active-duration"));
//				if(duration >-1){
//					//Date lastDateTime = DateStorageUtil.getStorageUtil().parseFromStorage(lastTime);
//					double eslapsedPeriod =DateStorageUtil.getStorageUtil().compareStorateDateWithCurrentTime(lastTime);
//					if( eslapsedPeriod > duration){
//						inReq.putPageValue("inactive", true);
//						inReq.putSessionValue("inactive", true);
//						inReq.putPageValue("inactiveuser", inUser);
//						inReq.putSessionValue("active-duration", String.valueOf(duration));
//						return false;
//					}
//					
//				}
//			}
			
			
			inReq.removeSessionValue("userprofile");
			inReq.putSessionValue("user", inUser);
			// user is now logged in
			String sendToOld = (String) inReq.getSessionValue("fullOriginalEntryPage");
			if (sendTo == null || sendTo.trim().length() == 0)
			{

				if (inReq.getRequest() != null)
				{
					String referrer = inReq.getRequest().getHeader("REFERER");
					if (sendToOld != null && !sendToOld.equals(referrer))
					{
						sendTo = sendToOld;
					}
					inReq.removeSessionValue("originalEntryPage");
					inReq.removeSessionValue("fullOriginalEntryPage");
				}
			}
			if (sendTo == null)
			{
				String appid = inReq.findValue("applicationid");
				if( appid != null)
				{
					sendTo = "/" + appid + "/index.html";
				}
				else
				{
					sendTo = "/index.html";
				}
			}

			savePasswordAsCookie(inUser, inReq);
			String cancelredirect = inReq.findValue("cancelredirect");
			if (!Boolean.parseBoolean(cancelredirect))
			{
				sendTo = sendTo.replace("oemaxlevel=", "canceloemaxlevel=");
				inReq.redirect(sendTo);
			}
			return true;
		}
		else
		{
			if (disable)
			{
				String failedLoginCount = inUser.get("failedlogincount");
				int fails = 0;
				if (failedLoginCount != null)
				{
					fails = Integer.parseInt(failedLoginCount);
				}
				fails++;
				inUser.setProperty("failedlogincount", String.valueOf(fails));
				if (fails >= maxattemps)
				{
					{
						User user = inReq.getUser();
						if( user != null)
						{
							String md5 = getCookieEncryption().getPasswordMd5(user.getPassword());
							String value = user.getUserName() + "md542" + md5;
							inReq.putPageValue("entermediakey", value);
						}
					}	
					inUser.setEnabled(false);
				}
				getUserManager().saveUser(inUser);

			}

		//	inReq.putSessionValue("oe-exception", "Invalid Logon");
			inReq.putPageValue("oe-exception", "Invalid Logon");
			return false;
		}

	}

	public void savePasswordAsCookie(User user, WebPageRequest inReq) throws OpenEditException
	{
		if (user.isVirtual())
		{
			log.debug("User is virtual. Not saving cookie");
			return;
		}
		HttpServletResponse res = inReq.getResponse();
		if (res != null)
		{
			String name = createMd5CookieName(inReq);

			try
			{
				String md5 = getCookieEncryption().getPasswordMd5(user.getPassword());
				String value = user.getUserName() + "md542" + md5;
				Cookie cookie = new Cookie(name, value);
				cookie.setMaxAge(Integer.MAX_VALUE);
				cookie.setPath("/"); // http://www.unix.org.ua/orelly/java-ent/servlet/ch07_04.htm
				res.addCookie(cookie);
				inReq.putPageValue("entermediakey", value);
			}
			catch ( Exception ex)
			{
				throw new OpenEditException(ex);
			}
			//TODO: Add a new alternative cookie that will auto login the user by passing the md5 of a secret key + their password
			//TODO: If the MD5 matches on both sides then we are ok to log them in

		}
	}
	public void loadEnterMediaKey(WebPageRequest inReq)
	{
		User user = inReq.getUser();
		if( user != null)
		{
			String md5 = getCookieEncryption().getPasswordMd5(user.getPassword());
			String value = user.getUserName() + "md542" + md5;
			inReq.putPageValue("entermediakey", value);
		}
	}
	public void logout(WebPageRequest inReq) throws OpenEditException
	{
		User user = (User) inReq.getSessionValue("user");
		if (user == null)
		{
			//this user is already logged out
			return;
		}
		getUserManager().logout(user);

		Enumeration enumeration = inReq.getSession().getAttributeNames();
		List toremove = new ArrayList();
		while (enumeration.hasMoreElements())
		{
			String id = (String) enumeration.nextElement();
			toremove.add(id);
		}
		for (Iterator iter = toremove.iterator(); iter.hasNext();)
		{
			String id = (String) iter.next();
			inReq.removeSessionValue(id);
			// inReq.removeSessionValue("editMode"); //legacy
			// inReq.removeSessionValue("username"); //legacy
			// inReq.removeSessionValue("user");
		}

		inReq.removePageValue("user");
		inReq.removePageValue("userprofile");
		removeCookie(inReq);

		String referrer = inReq.getRequestParameter("editingPath");
		if (referrer != null && !referrer.startsWith("http"))
		{
			Page epath = getPageManager().getPage(referrer);
			if (referrer.indexOf("/openedit") >= 0 || !epath.isHtml() || !epath.exists())
			{
				referrer = null;
			}
		}
		if (referrer != null)
		{
			inReq.redirect(referrer);
		}
	}

	protected void removeCookie(WebPageRequest inReq)
	{
		HttpServletResponse res = inReq.getResponse();
		if (res != null)
		{
			Cookie cookie = new Cookie(createMd5CookieName(inReq), "none");
			cookie.setMaxAge(0);
			cookie.setPath("/"); // http://www.unix.org.ua/orelly/java-ent/servlet/ch07_04.htm
			res.addCookie(cookie);
		}
	}

	public void autoLogin(WebPageRequest inReq) throws OpenEditException
	{
		if (inReq.getUser() != null)
		{
			return;
		}
		if (Boolean.parseBoolean(inReq.getContentProperty("oe.usernameinheader")))
		{
			autoLoginFromRequest(inReq);
		}
		if (inReq.getSessionValue("autologindone") == null)
		{
			readPasswordFromCookie(inReq);
		}
		if( inReq.getUser() == null)
		{
			String md5 = inReq.getRequestParameter(ENTERMEDIAKEY);
			if( md5 != null)
			{
				autoLoginFromMd5Value(inReq, md5);
			}
		}

//			
//			String login = inReq.getRequestParameter("accountname");
//			if (login != null)
//			{
//				quickLogin(inReq);
//			}

	}

	protected void quickLogin(WebPageRequest inReq) throws OpenEditException
	{
		if (inReq.getUser() == null)
		{
			String username = inReq.getRequestParameter("accountname");
			String password = inReq.getRequestParameter("password");
			if (password == null)
			{
				password = inReq.getRequestParameter("code");
				password = getUserManager().getStringEncryption().decrypt(password);
			}
			if (password == null)
			{
				return;
			}
			User user = getUserManager().getUser(username);
			if (user == null)
			{
				return;
			}
			if (!getUserManager().authenticate(user, password))
			{
				throw new OpenEditException("Did not authenticate: " + username);
			}
			else
			{
				inReq.setUser(user);
			}
		}
	}

	protected void autoLoginFromRequest(WebPageRequest inRequest)
	{
		String username = inRequest.getRequest().getRemoteUser();
		if (username == null)
		{
			return;
		}
		if (inRequest.getUser() != null)
		{
			return;
		}
		User user = getUserSearcher().getUser(username);

		if (user == null)
		{
			String groupname = inRequest.getPageProperty("autologingroup");
			if (groupname != null)
			{
				user = getUserManager().createGuestUser(username, null, groupname);
			}
		}
		if (user != null)
		{
			inRequest.putProtectedPageValue(PageRequestKeys.USER, user);
		}
	}
	String createMd5CookieName(WebPageRequest inReq)
	{
		String home = (String) inReq.getPageValue("home");
		String name = ENTERMEDIAKEY + home;
		return name;
	}

	protected void readPasswordFromCookie(WebPageRequest inReq) throws OpenEditException
	{
		// see if we have a coookie for this person with their encrypted password
		// in it
		HttpServletRequest req = inReq.getRequest();
		if (req != null)
		{
			Cookie[] cookies = req.getCookies();

			if (cookies != null)
			{
				String id = createMd5CookieName(inReq);
				for (int i = 0; i < cookies.length; i++)
				{
					Cookie cook = cookies[i];
					if (cook.getName() != null)
					{
						if (id.equals(cook.getName()) && autoLoginFromMd5Value(inReq, cook.getValue()))
						{
							return;
						}
						else
						{
							cook.setMaxAge(0); // remove the cookie
							inReq.getResponse().addCookie(cook);
						}
					}
				}
			}
		}
	}

	
	protected boolean autoLoginFromMd5Value(WebPageRequest inReq, String uandpass)
	{
		//get the password expiry in days
		int pwd_expiry_in_days = 1;
		String str = inReq.getPageProperty("temporary_password_expiry");
		if (str != null && !str.isEmpty()){
			try{
				pwd_expiry_in_days = Integer.parseInt(str);
				if (pwd_expiry_in_days < 1) pwd_expiry_in_days = 1;//default if malformed
			}catch(NumberFormatException e){}
		}
		log.info("Password is set to expire in "+pwd_expiry_in_days+" days");
		//String uandpass = cook.getValue();
		if (uandpass != null)
		{
			int split = uandpass.indexOf("md542");
			if (split == -1)
			{
				return false;
			}
			String username = uandpass.substring(0, split);

			User user = getUserSearcher().getUser(username);
			if (user != null && user.getPassword() != null)
			{
				String md5 = uandpass.substring(split + 5);
				
				//if timestamp included, check whether the autologin has expired
				if ((split = md5.indexOf(TIMESTAMP)) != -1){
					String tsenc = md5.substring(split+TIMESTAMP.length());
					md5 = md5.substring(0,split);
					try{
						String ctext = getCookieEncryption().decrypt(tsenc);
						long ts = Long.parseLong(ctext);
						long current = new Date().getTime();
						if ( (current - ts) > (pwd_expiry_in_days * MILLISECONDS_PER_DAY) ){
							log.info("Autologin has expired, redirecting to login page");
							return false;
						} else {
							log.info("Autologin has not expired, processing md5 password");
						}
					}catch (OpenEditException oex){
						log.error(oex.getMessage(),oex);
						return false;
					}catch (NumberFormatException nfx){
						log.error(nfx.getMessage(),nfx);
						return false;
					}
				} else {
					log.info("Autologin does not have a timestamp");
				}
				
				try
				{
					String hash = getCookieEncryption().getPasswordMd5(user.getPassword());
					if (md5.equals(hash))
					{
						inReq.putSessionValue("user", user);
						return true;
					}
					else
					{
						log.info("Auto login did not work " + username + " md5 " + md5);
						return false;
					}
				}
				catch (Exception ex)
				{
					throw new OpenEditException(ex);
				}
			}
		}
		return false;
	}

	public void forwardToSecureSocketsLayer(WebPageRequest inReq)
	{
		String useSecure = inReq.getPage().get("useshttps");

		if (Boolean.parseBoolean(useSecure) && inReq.getRequest() != null)
		{
			String host = inReq.getPage().get("hostname");
			if (host == null)
			{
				host = inReq.getPage().get("hostName");
			}
			if (host != null && !inReq.getRequest().isSecure())
			{
				String path = "https://" + host + inReq.getPathUrl();
				log.info("Forward to address " + path);
				inReq.redirect(path);
			}
		}
	}

	/**
	 * This is deprecated because it can't handle directory redirects and is
	 * buggy and hard to read. Use redirectHost and redirectInternal instead.
	 * 
	 * @param inReq
	 * @throws OpenEditException
	 * @deprecated
	 */
	public void redirect(WebPageRequest inReq) throws OpenEditException
	{
		String path = inReq.getCurrentAction().getChildValue("redirectpath");
		if (path == null)
		{
			path = inReq.getPage().get("redirectpath");
		}
		if (path != null && inReq.getRequest() != null)
		{
			URLUtilities utils = (URLUtilities) inReq.getPageValue(PageRequestKeys.URL_UTILITIES);
			if (path.endsWith("/"))
			{
				path = path.substring(0, path.length() - 1);
			}
			List host = inReq.getCurrentAction().getConfig().getChildren("host");
			if (host.size() > 0)
			{
				String server = utils.buildRoot(); //http://localhost:8080/
				boolean found = false;
				for (Iterator iterator = host.iterator(); iterator.hasNext();)
				{
					Configuration conf = (Configuration) iterator.next();
					//verify the host
					String hostval = conf.getValue();
					log.debug("Checking [" + server + "] starts with [" + hostval + "]");
					if (server.startsWith(hostval))
					{
						found = true;
						break;
					}
				}
				if (!found)
				{
					log.info("Host did not match, was [" + server + "]");
					return;
				}
			}
			int indestpath = path.indexOf("*"); //http://xyz/*
			if (indestpath > -1)
			{
				//this is a dynamic redirect path
				//http://xyz/* -> http://xyz/somepage.html
				//take off a part of the path before the *?
				String begin = path.substring(0, indestpath);

				String ending = utils.requestPathWithArgumentsNoContext();
				if (ending.startsWith("/") == true)
				{
					ending = ending.substring(1, ending.length());
				}
				String server = utils.buildRoot();

				String redirectPath = null;
				if (path.startsWith("http"))
				{
					redirectPath = begin + "/" + PathUtilities.extractFileName(ending);
				}
				else
				{
					redirectPath = begin + ending; //this does not handle subdirectory redirects
				}

				if (!redirectPath.equals(server))
				{
					inReq.redirectPermanently(redirectPath);
				}
				else
				{
					throw new OpenEditException("Infinite loop to forward to " + redirectPath);
				}
			}
			else if (path.startsWith("http"))
			{
				//		    	String fixedpath = path;
				//		    	String domain = utils.siteRoot();
				//		    	if( domain.startsWith("https://") && !fixedpath.startsWith("https://") )
				//		    	{
				//		    		fixedpath = "https://" + path.substring("https://".length()-1, path.length()); 
				//		    	}
				//		    	if( !fixedpath.startsWith(domain) )
				//		    	{
				//		    		//see if it exists locally
				//		    		if ( inReq.getContentPage().exists() )
				//		    		{
				//		    			String newurl = fixedpath + utils.requestPathWithArguments();
				//		    			inReq.redirectPermanently(newurl);
				//		    		}
				//		    		else
				//		    		{

				//http://localhost:8080/
				String server = utils.buildRoot();
				if (!path.startsWith(server))
				{
					inReq.redirectPermanently(path);
				}
				//		    	}
			}
			else
			{
				if (!inReq.getPath().equals(path))
				{
					inReq.redirectPermanently(path);
				}
			}
		}
	}

	/**
	 * This is used to direct to a different host. I can be used in conjunction
	 * with redirectInternal(). <path-action name="Admin.redirectHost">
	 * <redirecthost>http://xyz.com/</redirecthost> </path-action>
	 * 
	 * @param inReq
	 * @throws OpenEditException
	 */
	public void redirectHost(WebPageRequest inReq) throws OpenEditException
	{
		if( inReq.getRequest() == null)
		{
			return;
		}
		String host = inReq.getCurrentAction().getChildValue("redirecthost");
		URLUtilities utils = (URLUtilities) inReq.getPageValue(PageRequestKeys.URL_UTILITIES);
		if (utils == null)
		{
			utils = new URLUtilities(inReq.getRequest(), inReq.getResponse());
		}
		String ending = utils.requestPathWithArgumentsNoContext();
		String server = utils.buildRoot();
		if (server != null && server.endsWith("/"))
		{
			server = server.substring(0, server.length() - 1);
		}
		if (host.endsWith("/"))
		{
			host = host.substring(0, host.length() - 1);
		}


		if (!host.equals(server))
		{
			String redirectPath = host + ending;
			//log.info("Redirecting " + host + " AND " + server);
			inReq.redirectPermanently(redirectPath);
		}
		else
		{
			return;
		}
	}

	/**
	 * This is used to redirect between pages on the same server. It may be used
	 * in conjunction with redirectHost(). This should be used instead of the
	 * old Admin.redirect <path-action name="Admin.redirectInternal"> <!--This
	 * is an example of directory substitution-->
	 * <redirectpath>/newpath/</redirectpath>
	 * <redirectroot>/oldpath/</redirectroot> </path-action> <path-action
	 * name="Admin.redirectInternal"> <!--This is an example of absolute
	 * substitution--> <redirectpath>/newpath/index.html</redirectpath>
	 * <redirectroot>*</redirectroot> </path-action>
	 * 
	 * @param inReq
	 * @throws OpenEditException
	 */
	public void redirectInternal(WebPageRequest inReq) throws OpenEditException
	{
		String path = inReq.getCurrentAction().getChildValue("redirectpath");
		String rootdir = inReq.getCurrentAction().getChildValue("redirectroot");
		URLUtilities utils = (URLUtilities) inReq.getPageValue(PageRequestKeys.URL_UTILITIES);
		String server = utils.buildRoot();
		if (server.endsWith("/"))
		{
			server = server.substring(0, server.length() - 1);
		}
		String ending = utils.requestPathWithArgumentsNoContext();
		String newEnding;

		if (rootdir.equals("*"))
		{
			newEnding = path;
		}
		else
		{
			newEnding = ending.replace(rootdir, path);
		}

		String redirectPath = server + newEnding;

		if (!newEnding.equals(ending))
		{
			inReq.redirectPermanently(redirectPath);
		}
		else
		{
			return;
		}
	}

	public void redirectToOriginal(WebPageRequest inReq)
	{
		String editPath = inReq.getRequestParameter("editPath");
		String orig = inReq.getRequestParameter("origURL");
		if (orig == null)
		{
			orig = inReq.findValue("origURL");
		}
		if (orig != null)
		{
			if (orig.indexOf("?") == -1 && editPath != null)
			{
				inReq.redirect(orig + "?path=" + editPath + "&cache=false");
			}
			else
			{
				inReq.redirect(orig);
			}
		}
		else
		{
			// log.error("No origURL specified");
		}
	}

	public SendMailModule getSendMailModule()
	{
		return sendMailModule;
	}

	public void setSendMailModule(SendMailModule sendMailModule)
	{
		this.sendMailModule = sendMailModule;
	}

	public void toogleAdminToolbar(WebPageRequest inReq)
	{
		User user = inReq.getUser();
		if (user != null)
		{
			boolean bol = user.getBoolean("openadmintoolbar");
			if (bol)
			{
				user.put("openadmintoolbar", "false");
			}
			else
			{
				user.put("openadmintoolbar", "true");
			}
			if (!user.isVirtual())
			{
				getUserSearcher().saveData(user, inReq.getUser());
			}
		}
		redirectToOriginal(inReq);
	}

	public StringEncryption getCookieEncryption()
	{
//		if (fieldCookieEncryption == null)
//		{
//			fieldCookieEncryption = new StringEncryption();
////			String KEY = "SomeWeirdReallyLongStringYUITYGFNERDF343dfdGDFGSDGGD";
////			fieldCookieEncryption.setEncryptionKey(KEY);
//		}
		return fieldCookieEncryption;
	}

	public void setCookieEncryption(StringEncryption inCookieEncryption)
	{
		fieldCookieEncryption = inCookieEncryption;
	}

	public void toggleProperty(WebPageRequest inReq) throws Exception
	{
		User user = inReq.getUser();
		if (user != null)
		{
			String id = inReq.getRequestParameter("propertyid");
			if (id == null)
			{
				id = inReq.getRequestParameter("id");
			}
			if (id != null)
			{
				boolean has = user.hasProperty(id);
				if (has)
				{
					user.remove(id);
				}
				else
				{
					user.put(id, String.valueOf(has));
				}
				getUserManager().saveUser(user);
			}
		}
	}

	public String getTheme(WebPageRequest inReq) throws Exception
	{
		String theme = inReq.findValue("themeprefix");
		inReq.putPageValue("themeprefix", theme);
		return theme;
	}

	protected Page findWelcomePage(Page inDirectory) throws OpenEditException
	{
		String dir = inDirectory.getPath();
		if (!dir.endsWith("/"))
     	{
     		dir =  dir + "/";
     	}
		for (Iterator iterator = getWelcomeFiles().iterator(); iterator.hasNext();)
		{
			String index = (String) iterator.next();
	    	if( getPageManager().getRepository().doesExist( dir + index))
		    {
	    		return getPageManager().getPage(dir + index,true);
		    }
		}
		return getPageManager().getPage( dir + "index.html",true);
	}
	public void checkExist(WebPageRequest inReq) throws Exception
	{
		check404(inReq);
	}

	public void check404(WebPageRequest inReq) throws Exception
	{
		boolean exist = inReq.getPage().exists(); 
		if (  exist )
		{
			return;
		}
	
		
		PageStreamer streamer = inReq.getPageStreamer();
		if(streamer != null)
		{
			streamer.getWebPageRequest().putPageValue("pathNotFound",inReq.getPath());
		}
		String isVirtual = inReq.getPage().get("virtual");
		if ( Boolean.parseBoolean(isVirtual))
		{
			return;
		}
		
		URLUtilities utils = (URLUtilities)inReq.getPageValue(PageRequestKeys.URL_UTILITIES);

		if( utils != null)
		{
			//redirecting only works relative to a webapp
			if(streamer != null)
			{
				streamer.getWebPageRequest().putPageValue("forcedDestinationPath", utils.requestPathWithArgumentsNoContext() );
			}
		}
		PageManager pageManager = getPageManager();
		if ( inReq.getPage().isHtml() &&  inReq.isEditable() )
		{
			Page wizard = pageManager.getPage("/system/nopagefound.html");
			if ( wizard.exists() )
			{
				inReq.getPageStreamer().include( wizard);
				inReq.setHasRedirected(true);
				return;
			}
		}
		if ( !inReq.getPage().isHtml() )
		{
			HttpServletResponse response = inReq.getResponse();
			if( response  != null)
			{
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				inReq.setHasRedirected(true);
				return;
			}
		}
		
		if ( inReq.getContentPage().getPath().equals( inReq.getPath()))
		{
			//log.info( "Could not use  add page wizard. 404 error on: " + inReq.getPath() );
			Page p404 = pageManager.getPage("/error404.html");
			if ( p404.exists() )
			{
				HttpServletResponse response = inReq.getResponse();
				if ( response != null)
				{
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
				inReq.putProtectedPageValue("content", p404);
				//inReq.forward(p404.getPath());
				return;
			}
			else
			{
				log.error( "Could not report full 404 error on: " + inReq.getPath() + ". Make sure the 404 error page exists " + p404.getPath());
				//other users will get the standard file not found error
				HttpServletResponse response = inReq.getResponse();
				if( response != null)
				{
					response.sendError(HttpServletResponse.SC_NOT_FOUND);
					inReq.setHasRedirected(true);
				}
			}
		}
		else
		{
			inReq.getWriter().write("404 on " + inReq.getPath());
			inReq.getWriter().flush();
			inReq.setHasRedirected(true);
			
		}
	}
	public void createGuestUser(WebPageRequest inReq) {
		User user = inReq.getUser();
		if (user == null) {
			Group guest = getUserManager().getGroup("guest");
			if (guest == null) {
				getUserManager().createGroup("guest");
			}

			user = getUserManager().createGuestUser(null, null, "guest");
			inReq.putPageValue("user", user);
			inReq.putSessionValue("user", user);
		}

	}
}
