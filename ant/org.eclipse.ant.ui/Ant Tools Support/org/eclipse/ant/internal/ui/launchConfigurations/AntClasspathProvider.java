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

package org.eclipse.ant.internal.ui.launchConfigurations;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.ant.core.AntCorePreferences;
import org.eclipse.ant.internal.ui.model.AntUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.StandardClasspathProvider;

public class AntClasspathProvider extends StandardClasspathProvider {

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IRuntimeClasspathProvider#computeUnresolvedClasspath(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public IRuntimeClasspathEntry[] computeUnresolvedClasspath(ILaunchConfiguration configuration) throws CoreException {
		//no need to add Eclipse extension point URLs if going to build
		//in separate VM..except for the remoteAnt.jar
		boolean separateVM= (null != configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_TYPE, (String)null));
		URL[] antURLs= AntUtil.getCustomClasspath(configuration);
		AntCorePreferences prefs= AntCorePlugin.getPlugin().getPreferences();
		if (antURLs == null) {
			if (separateVM) {
				antURLs= prefs.getRemoteAntURLs();
			} else {
				antURLs = prefs.getURLs();
			}
		} else {
			if (separateVM) {
				List fullClasspath= new ArrayList(40);
				fullClasspath.addAll(Arrays.asList(antURLs));
				fullClasspath.addAll(Arrays.asList(prefs.getRemoteExtraClasspathURLs()));
				antURLs= (URL[])fullClasspath.toArray(new URL[fullClasspath.size()]);
			} else {
				List fullClasspath= new ArrayList(50);
				fullClasspath.addAll(Arrays.asList(antURLs));
				fullClasspath.addAll(Arrays.asList(prefs.getExtraClasspathURLs()));
				antURLs= (URL[])fullClasspath.toArray(new URL[fullClasspath.size()]);
			}
		}
		
		IVMInstall vm = JavaRuntime.computeVMInstall(configuration);
		IRuntimeClasspathEntry[] rtes = new IRuntimeClasspathEntry[antURLs.length + 1];
		IPath containerPath = new Path(JavaRuntime.JRE_CONTAINER);
		containerPath = containerPath.append(new Path(vm.getVMInstallType().getId()));
		containerPath = containerPath.append(new Path(vm.getName()));
		
		rtes[0] = JavaRuntime.newRuntimeContainerClasspathEntry(containerPath, IRuntimeClasspathEntry.STANDARD_CLASSES);
		
		for (int j = 0; j < antURLs.length; j++) {
			URL url = antURLs[j];
			IPath path= new Path(url.getPath());
			IRuntimeClasspathEntry antEntry= JavaRuntime.newArchiveRuntimeClasspathEntry(path);
			antEntry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
			rtes[j+1]= antEntry;
		}
		return rtes;		
	}
}
