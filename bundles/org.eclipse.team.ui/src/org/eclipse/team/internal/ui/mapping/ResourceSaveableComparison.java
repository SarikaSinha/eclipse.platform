/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.mapping;

import org.eclipse.compare.*;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.synchronize.LocalResourceTypedElement;
import org.eclipse.team.ui.mapping.ISynchronizationCompareInput;
import org.eclipse.team.ui.mapping.SaveableComparison;

/**
 * A saveable compare model that wraps an {@link IFile} based compare input. This saveable is
 * created by the {@link CompareEditorInput} instead of the {@link ResourceDiffCompareInput}
 * because it needs access to the compare editor input in order to flush the viewers. Other model-based
 * compare inputs do not need this since the compare input and viewers should be provided by the same model.
 * <p>
 * This saveable assumes that the left node of the compare input is an {@link LocalResourceTypedElement}.
 */
public class ResourceSaveableComparison extends SaveableComparison implements IPropertyChangeListener {

	private final ICompareInput input;
	private final CompareEditorInput editorInput;
	private boolean isSaving;
	private IContentChangeListener contentChangeListener;
	private final String title;
	
	/**
	 * Create the resource-based saveable comparison.
	 * @param title the title
	 * @param input the compare input to be save
	 * @param editorInput the editor input containing the comparison
	 */
	public ResourceSaveableComparison(String title, ICompareInput input, CompareEditorInput editorInput) {
		this.title = title;
		this.input = input;
		this.editorInput = editorInput;
		initializeContentChangeListeners();
	}
	
	private void initializeContentChangeListeners() {
		// We need to listen to saves to the input to catch the case
		// where Save was picked from the context menu
		ITypedElement te = input.getLeft();
		if (te instanceof IContentChangeNotifier) {
			if (contentChangeListener == null) {
				contentChangeListener = new IContentChangeListener() {
					public void contentChanged(IContentChangeNotifier source) {
						try {
							if(! isSaving) {
								performSave(new NullProgressMonitor());
							}
						} catch (CoreException e) {
							TeamUIPlugin.log(e);
						}
					}
				};
			}
			((IContentChangeNotifier) te).addContentChangeListener(contentChangeListener);
		}
	}
	
	/**
	 * Dispose of the saveable.
	 */
	public void dispose() {
		if (contentChangeListener != null) {
			ITypedElement te = input.getLeft();
			if (te instanceof IContentChangeNotifier) {
				((IContentChangeNotifier) te).removeContentChangeListener(contentChangeListener);
			}
		}
		// Discard of the left buffer
		ITypedElement left = input.getLeft();
		if (left instanceof LocalResourceTypedElement)
			 ((LocalResourceTypedElement) left).discardBuffer();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.SaveableCompareModel#performSave(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void performSave(IProgressMonitor monitor) throws CoreException {
		if (checkForUpdateConflicts()) {
			return;
		}
		ITypedElement left = input.getLeft();
		if (left instanceof LocalResourceTypedElement) {
			LocalResourceTypedElement te = (LocalResourceTypedElement) left;
			if (te.isConnected()) {
				te.saveDocument(true, monitor);
				// Saving the document should fire the necessary updates
				return;
			}
		}
		try {
			isSaving = true;
			monitor.beginTask(null, 100);
			// First, we need to flush the viewers so the changes get buffered
			// in the input
			flushViewers(Policy.subMonitorFor(monitor, 40));
			// Then we tell the input to commit its changes
			// Only the left is ever saveable
			if (left instanceof LocalResourceTypedElement) {
				LocalResourceTypedElement te = (LocalResourceTypedElement) left;
				te.commit(Policy.subMonitorFor(monitor, 60));
			}
		} finally {
			// Make sure we fire a change for the compare input to update the viewers
			fireInputChange();
			setDirty(false);
			isSaving = false;
			monitor.done();
		}
	}

	/**
	 * Flush the contents of any viewers into the compare input.
	 * @param monitor a progress monitor
	 * @throws CoreException
	 */
	protected void flushViewers(IProgressMonitor monitor) throws CoreException {
		editorInput.saveChanges(monitor);
	}

	/**
	 * Fire an input change for the compare input. By default, this method
	 * only works for {@link ResourceDiffCompareInput}. Subclass may override
	 * for other input types.
	 */
	protected void fireInputChange() {
		if (input instanceof ResourceDiffCompareInput) {
			ResourceDiffCompareInput rdci = (ResourceDiffCompareInput) input;
			rdci.fireChange();
		}
	}

	/**
	 * Check whether there is a conflicting save on the file.
	 * @return <code>true</code> if there was and the user chose to cancel the operation
	 */
	private boolean checkForUpdateConflicts() {
		if(hasSaveConflict()) {
			final MessageDialog dialog = 
				new MessageDialog(TeamUIPlugin.getStandardDisplay().getActiveShell(), 
						TeamUIMessages.SyncInfoCompareInput_0,  
						null, 
						TeamUIMessages.SyncInfoCompareInput_1,  
						MessageDialog.QUESTION,
					new String[] {
						TeamUIMessages.SyncInfoCompareInput_2, 
						IDialogConstants.CANCEL_LABEL}, 
					0);
			
			int retval = dialog.open();
			switch(retval) {
				// save
				case 0: 
					return false;
				// cancel
				case 1:
					return true;
			}
		}
		return false;
	}

	private boolean hasSaveConflict() {
		ITypedElement left = input.getLeft();
		if (left instanceof LocalResourceTypedElement) {
			LocalResourceTypedElement te = (LocalResourceTypedElement) left;
			return !te.isSynchronized();
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.SaveableCompareModel#isDirty()
	 */
	public boolean isDirty() {
		// We need to get the dirty state from the compare editor input
		// since it is our only connection to the merge viewer
		return editorInput.isSaveNeeded();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.SaveableCompareModel#setDirty(boolean)
	 */
	protected void setDirty(boolean dirty) {
		// We need to set the dirty state on the compare editor input
		// since it is our only connection to the merge viewer
		editorInput.setDirty(dirty);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.mapping.SaveableCompareModel#performRevert(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void performRevert(IProgressMonitor monitor) {
		// Only the left is ever editable
		ITypedElement left = input.getLeft();
		if (left instanceof LocalResourceTypedElement)
			 ((LocalResourceTypedElement) left).discardBuffer();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.Saveable#getName()
	 */
	public String getName() {
		return input.getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.Saveable#getToolTipText()
	 */
	public String getToolTipText() {
		String fullPath;
		if (input instanceof ISynchronizationCompareInput) {
			ISynchronizationCompareInput mci = (ISynchronizationCompareInput) input;
			fullPath = mci.getFullPath();
		} else {
			fullPath = getName();
		}
		return NLS.bind(TeamUIMessages.SyncInfoCompareInput_tooltip, new String[] { Utils.shortenText(30, title), fullPath });
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.Saveable#getImageDescriptor()
	 */
	public ImageDescriptor getImageDescriptor() {
		Image image = input.getImage();
		if (image != null)
			return ImageDescriptor.createFromImage(image);
		return TeamUIPlugin.getImageDescriptor(ITeamUIImages.IMG_SYNC_VIEW);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent e) {
		String propertyName= e.getProperty();
		if (CompareEditorInput.DIRTY_STATE.equals(propertyName)) {
			boolean changed= false;
			Object newValue= e.getNewValue();
			if (newValue instanceof Boolean)
				changed= ((Boolean)newValue).booleanValue();
			setDirty(changed);
		}			
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.Saveable#equals(java.lang.Object)
	 */
	public boolean equals(Object object) {
		if (object instanceof ResourceSaveableComparison) {
			ResourceSaveableComparison rscm = (ResourceSaveableComparison) object;
			return rscm.input.equals(input);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.Saveable#hashCode()
	 */
	public int hashCode() {
		return input.hashCode();
	}

	/**
	 * Return the compare input that is managed by this saveable.
	 * @return the compare input that is managed by this saveable
	 */
	public ICompareInput getInput() {
		return input;
	}
}
