/*******************************************************************************
 * Copyright (c) 2003, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ant.internal.ui.refactoring;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.ant.internal.ui.launchConfigurations.IAntLaunchConfigurationConstants;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.externaltools.internal.model.IExternalToolConstants;

public class LaunchConfigurationBuildfileChange extends Change {
	
	private ILaunchConfiguration fLaunchConfiguration;
	private String fNewBuildfileLocation;
	private String fNewProjectName;
	private String fNewLaunchConfigurationName;
	private String fOldBuildfileLocation;
	private String fOldProjectName;
	private ILaunchConfigurationWorkingCopy fNewLaunchConfiguration;
    private String fNewConfigContainerName;

    /**
     * Create a change for each launch configuration which needs to be updated for this IJavaProject rename.
     */
    public static Change createChangesForProjectRename(IProject project, String newProjectName) throws CoreException {
        String projectName= project.getDescription().getName();
        ILaunchConfiguration[] configs = getAntLaunchConfigurations();
        List changes= createChangesForProjectRename(project, configs, projectName, newProjectName);
        return createChangeFromList(changes, RefactoringMessages.LaunchConfigurationProjectMainTypeChange_7); 
    }
    
    /**
     * Create a change for each launch configuration which needs to be updated for this buildfile rename.
     */
    public static Change createChangesForBuildfileRename(IFile file, String newBuildfileName) throws CoreException {
        IContentDescription description= null;
        try {
            description= file.getContentDescription();
        } catch (CoreException e) {
            //missing file
            return null;
        }
        if (description == null) {
            return null;
        }
        IContentType contentType= description.getContentType();
        if (contentType == null || !AntCorePlugin.ANT_BUILDFILE_CONTENT_TYPE.equals(contentType.getId())) {
            return null; //not an Ant buildfile
        }
        ILaunchConfiguration[] configs = getAntLaunchConfigurations();
        List changes= createChangesForBuildfileRename(file, configs, file.getProject().getName(), newBuildfileName);
        return createChangeFromList(changes, RefactoringMessages.LaunchConfigurationProjectMainTypeChange_7); 
    }

    private static ILaunchConfiguration[] getAntLaunchConfigurations() throws CoreException {
        ILaunchManager manager= DebugPlugin.getDefault().getLaunchManager();
        // Ant launch configurations
        ILaunchConfigurationType configurationType= manager.getLaunchConfigurationType(IAntLaunchConfigurationConstants.ID_ANT_LAUNCH_CONFIGURATION_TYPE);
        ILaunchConfiguration[] configs= manager.getLaunchConfigurations(configurationType);
        return configs;
    }
    
    /**
     * Take a list of Changes, and return a unique Change, a CompositeChange, or null.
     */
    private static Change createChangeFromList(List changes, String changeLabel) {
        int nbChanges= changes.size();
        if (nbChanges == 0) {
            return null;
        } else if (nbChanges == 1) {
            return (Change) changes.get(0);
        } else {
            return new CompositeChange(changeLabel, (Change[])changes.toArray(new Change[changes.size()]));
        }
    }
		
	/**
	 * Create a change for each launch configuration from the given list which needs 
	 * to be updated for this IProject rename.
	 */
	private static List createChangesForProjectRename(IProject project, ILaunchConfiguration[] configs, String projectName, String newProjectName) throws CoreException {
		List changes= new ArrayList();
		for (int i= 0; i < configs.length; i++) {
			ILaunchConfiguration launchConfiguration = configs[i];
			String launchConfigurationProjectName= launchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String)null);
			if (projectName.equals(launchConfigurationProjectName)) {
				LaunchConfigurationBuildfileChange change = new LaunchConfigurationBuildfileChange(launchConfiguration, null, null, newProjectName, false);
                String newContainerName = computeNewContainerName(project, launchConfiguration);
                if (newContainerName != null) {
                    change.setNewContainerName(newContainerName);
                }

				changes.add(change);
			}
		}
		return changes;
	}
    
    /**
     * Create a change for each launch configuration from the given list which needs 
     * to be updated for this buildfile rename.
     */
    private static List createChangesForBuildfileRename(IFile buildfile, ILaunchConfiguration[] configs, String projectName, String newBuildfileName) throws CoreException {
        List changes= new ArrayList();
        for (int i= 0; i < configs.length; i++) {
            ILaunchConfiguration launchConfiguration = configs[i];
            String launchConfigurationProjectName= launchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String)null);
            if (projectName.equals(launchConfigurationProjectName)) {
                LaunchConfigurationBuildfileChange change = new LaunchConfigurationBuildfileChange(launchConfiguration, buildfile.getName(), newBuildfileName, null, false);
                changes.add(change);
            }
        }
        return changes;
    }

    protected void setNewContainerName(String newContainerName) {
        fNewConfigContainerName = newContainerName;
    }

    private static String computeNewContainerName(IProject project, ILaunchConfiguration launchConfiguration) {
        IPath currentLocation = launchConfiguration.getLocation();
        IPath projectLocation = project.getLocation();
        if (projectLocation.isPrefixOf(currentLocation)) {
            String projectFile = new File(projectLocation.toOSString()).getAbsolutePath();
            String configDir = new File(currentLocation.toOSString()).getParent();
            return new String(configDir.substring(projectFile.length()));
        }
        return null;
    }
    
    protected LaunchConfigurationBuildfileChange(ILaunchConfiguration launchConfiguration, String oldBuildFileName, String newBuildfileName, String newProjectName, boolean undo) throws CoreException {
        fLaunchConfiguration= launchConfiguration;
        fNewLaunchConfiguration= launchConfiguration.getWorkingCopy();
        fNewBuildfileLocation= newBuildfileName;
        fNewProjectName= newProjectName;
        fOldBuildfileLocation= oldBuildFileName;
        fOldProjectName= fLaunchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String) null);
        if (fNewBuildfileLocation != null) {
            // generate the new configuration name
            String launchConfigurationName= fLaunchConfiguration.getName();
            fNewLaunchConfigurationName= launchConfigurationName.replaceAll(oldBuildFileName, newBuildfileName);
            if (launchConfigurationName.equals(fNewLaunchConfigurationName) || (!undo && DebugPlugin.getDefault().getLaunchManager().isExistingLaunchConfigurationName(fNewLaunchConfigurationName))) {
                fNewLaunchConfigurationName= null;
            }
        }
    }

    /* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#getName()
	 */
	public String getName() {
		if (fNewLaunchConfigurationName != null) {
			return MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_0, new String[] {fLaunchConfiguration.getName(), fNewLaunchConfigurationName}); 
		} 
		if (fNewProjectName == null) {
			return MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_1, new String[] {fLaunchConfiguration.getName()}); 
		}
		if (fNewBuildfileLocation == null) {
			return MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_2, new String[] {fLaunchConfiguration.getName()}); 
		}
		return MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_3, new String[] {fLaunchConfiguration.getName()}); 
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#initializeValidationData(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void initializeValidationData(IProgressMonitor pm) {
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#isValid(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		if (fLaunchConfiguration.exists()) {
			String buildFileLocation= fLaunchConfiguration.getAttribute(IExternalToolConstants.ATTR_LOCATION, ""); //$NON-NLS-1$
			if (fOldBuildfileLocation == null || (buildFileLocation.endsWith(fOldBuildfileLocation + '}') || buildFileLocation.endsWith(fOldBuildfileLocation))) {
				String projectName= fLaunchConfiguration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String)null);
				if (fOldProjectName.equals(projectName)) {
					return new RefactoringStatus();
				}
				return RefactoringStatus.createWarningStatus(MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_4, new String[] {fLaunchConfiguration.getName(), fOldProjectName})); 
			}
			return RefactoringStatus.createWarningStatus(MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_5, new String[] {fLaunchConfiguration.getName(), fOldBuildfileLocation})); 
		} 
		return RefactoringStatus.createFatalErrorStatus(MessageFormat.format(RefactoringMessages.LaunchConfigurationProjectMainTypeChange_6, new String[] {fLaunchConfiguration.getName()})); 
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#perform(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public Change perform(IProgressMonitor pm) throws CoreException {        
        if (fNewConfigContainerName != null) {
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IWorkspaceRoot root = workspace.getRoot();
            IProject project = root.getProject(fNewProjectName);
            IContainer container = (IContainer) project.findMember(fNewConfigContainerName);
            fNewLaunchConfiguration.setContainer(container);
        }

		String oldBuildfileLocation= fNewLaunchConfiguration.getAttribute(IExternalToolConstants.ATTR_LOCATION, ""); //$NON-NLS-1$
		String oldProjectName;
		if (fNewBuildfileLocation != null) {
            String newBuildFileLocation= oldBuildfileLocation.replaceFirst(fOldBuildfileLocation, fNewBuildfileLocation);
            fNewLaunchConfiguration.setAttribute(IExternalToolConstants.ATTR_LOCATION, newBuildFileLocation);
		} 
		if (fNewProjectName != null) {
			oldProjectName= fOldProjectName;
			fNewLaunchConfiguration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, fNewProjectName);
            String newBuildFileLocation= oldBuildfileLocation.replaceFirst(oldProjectName, fNewProjectName);
            fNewLaunchConfiguration.setAttribute(IExternalToolConstants.ATTR_LOCATION, newBuildFileLocation);
            
            String launchConfigurationName= fLaunchConfiguration.getName();
            fNewLaunchConfigurationName= launchConfigurationName.replaceFirst(oldProjectName, fNewProjectName);
            if (launchConfigurationName.equals(fNewLaunchConfigurationName) || DebugPlugin.getDefault().getLaunchManager().isExistingLaunchConfigurationName(fNewLaunchConfigurationName)) {
                fNewLaunchConfigurationName= null;
            }
		} else {
			oldProjectName= null;
		}
		if (fNewLaunchConfigurationName != null) {
			fNewLaunchConfiguration.rename(fNewLaunchConfigurationName);
		}

		fNewLaunchConfiguration.doSave();

		// create the undo change
		return new LaunchConfigurationBuildfileChange(fNewLaunchConfiguration, fNewBuildfileLocation, fOldBuildfileLocation, oldProjectName, true);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#getModifiedElement()
	 */
	public Object getModifiedElement() {
		return fLaunchConfiguration;
	}
}