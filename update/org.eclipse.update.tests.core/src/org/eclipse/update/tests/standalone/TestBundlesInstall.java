/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.update.tests.standalone;


import java.net.*;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.standalone.*;


public class TestBundlesInstall extends StandaloneManagerTestCase {
	private static boolean isInstalled;

	public TestBundlesInstall(String arg0) {
		super(arg0);
		isInstalled = false;
	}

	public void umSetUp() {
		super.umSetUp();
		//System.out.println("looking at configured sites available....");
		//checkConfiguredSites();
		
		String featureId = "com.example.bundle.feature";
		String version = "1.0.0";

		String fromRemoteSiteUrl = "http://"+getHttpHost()+":"+ getHttpPort()+ "/org.eclipse.update.tests.core.updatetests/bundleSite/";;
		if (!isInstalled) {
			System.out.println(
				"==============" + this.getClass() + "=============");
			StandaloneUpdateApplication app = new StandaloneUpdateApplication();
			try {
				exitValue = (Integer)app.run(getCommand(
					"install",
					featureId,
					version,
					null,
					fromRemoteSiteUrl,
					TARGET_FILE_SITE.getFile()));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			isInstalled = true;
		}
		
	}

	public void testPluginsExist() {
		ISite localSite = getConfiguredSite(TARGET_FILE_SITE);
		
		System.out.println("localSite: " + localSite.getURL().getFile());
		IPluginEntry[] pluginEntries = localSite.getPluginEntries();
		ArrayList list = new ArrayList();
		if (pluginEntries == null || pluginEntries.length == 0){
			System.err.println("No plugin entries on the target site");
			fail("No plugin entries on the target site");
		} else{
			for (int i = 0; i < pluginEntries.length; i++){
				System.out.println("found plugin: " + pluginEntries[i].getVersionedIdentifier().toString());
				list.add(pluginEntries[i].getVersionedIdentifier().toString());
			}
		}

		String[] pluginNames =
			{	"com.example.bundle.plugin_1.0.0",
				"com.example.bundle.plugin.ui_2.0.0",
				"com.example.bundle.fragment_1.0.0",
				"com.example.budle.fragment.ui_1.0.0"};
		assertTrue(checkFilesInList(pluginNames, list));
	}

	public void testFeaturesExist() {
		try {
			ISite localSite =  getConfiguredSite(TARGET_FILE_SITE);

			System.out.println("localSite: " + localSite.getURL().getFile());
			// get feature references 
			IFeatureReference[] localFeatures =
				localSite.getFeatureReferences();
			System.out.println("local currentCOnfigSite: " + localSite.getCurrentConfiguredSite());
			ArrayList list = new ArrayList();
			if (localFeatures == null || localFeatures.length == 0){
				System.err.println("No features on the target site");
				fail("No features on the target site");
			} else {
				for (int i = 0; i < localFeatures.length; i++)
					list.add(localFeatures[i].getVersionedIdentifier().toString());
			}
			String[] featureNames =
				{
					"com.example.bundle.feature_1.0.0"};
			assertTrue(checkFilesInList(featureNames, list));
		} catch (CoreException e) {
			System.err.println(e);
		}
	}

	// makes sure all files/directories in "names" are in the directory listing "list"
	public boolean checkFilesInList(
		String[] names,
		ArrayList list) {
		
		for (int i = 0; i < names.length; i++) {
			System.out.println(names[i]);
			if (!list.contains(names[i])){
				return false;
			}
		}
		return true;
	}

	// ensure exit without problems
	public void testExitValue() throws Exception {
		System.out.println("exitValue: " + exitValue);
		assertEquals(exitValue, IPlatformRunnable.EXIT_OK);
	}
}
