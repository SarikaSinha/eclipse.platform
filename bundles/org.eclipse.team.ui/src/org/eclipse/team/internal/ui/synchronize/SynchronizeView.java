/*******************************************************************************
 * Copyright (c) 2000, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.synchronize;

import java.util.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.internal.core.mapping.ResourceVariantFileRevision;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.internal.ui.history.FileRevisionEditorInput;
import org.eclipse.team.internal.ui.synchronize.actions.*;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.*;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

/**
 * Implements a Synchronize View that contains multiple synchronize participants. 
 */
public class SynchronizeView extends PageBookView implements ISynchronizeView, ISynchronizeParticipantListener, IPropertyChangeListener, ISaveablesSource, ISaveablePart, IShowInTarget {
	
	/**
	 * Suggested maximum length of participant names when shown in certain menus and dialog.
	 */
	public final static int MAX_NAME_LENGTH = 100;
	
	/**
	 * The participant being displayed, or <code>null</code> if none
	 */
	private ISynchronizeParticipant activeParticipantRef = null;
	
	/**
	 * Map of participants to dummy participant parts (used to close pages)
	 */
	private Map fParticipantToPart;
	
	/**
	 * Map of parts to participants
	 */
	private Map fPartToParticipant;

	/**
	 * Drop down action to switch between participants
	 */
	private SynchronizePageDropDownAction fPageDropDown;
	
	/**
	 * Action to remove the selected participant
	 */
	private PinParticipantAction fPinAction;
	
	/**
	 * Action to remove the currently shown participant
	 */
	private RemoveSynchronizeParticipantAction fRemoveCurrentAction;
	
	/**
	 * Action to remove all non-pinned participants
	 */
	private RemoveSynchronizeParticipantAction fRemoveAllAction;
	
	private ToggleLinkingAction fToggleLinkingAction;
	private boolean fLinkingEnabled;
	private OpenAndLinkWithEditorHelper fOpenAndLinkWithEditorHelper;

	/**
	 * Preference key to save
	 */
	private static final String KEY_LAST_ACTIVE_PARTICIPANT_ID = "lastactiveparticipant_id"; //$NON-NLS-1$
    private static final String KEY_LAST_ACTIVE_PARTICIPANT_SECONDARY_ID = "lastactiveparticipant_sec_id"; //$NON-NLS-1$
    private static final String KEY_LINK_WITH_EDITOR = "linkWithEditor"; //$NON-NLS-1$
	private static final String KEY_SETTINGS_SECTION= "SynchronizeViewSettings"; //$NON-NLS-1$


	/* (non-Javadoc)
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		Object source = event.getSource();
		if (source instanceof ISynchronizeParticipant) {
			if (event.getProperty().equals(IBasicPropertyConstants.P_TEXT)) {
				if (source.equals(getParticipant())) {
					updateTitle();
				}
			} else if (event.getProperty().equals(ModelSynchronizeParticipant.PROP_DIRTY)) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						firePropertyChange(PROP_DIRTY);
					}
				});
			} else if (event.getProperty().equals(ModelSynchronizeParticipant.PROP_ACTIVE_SAVEABLE)) {
				Saveable oldSaveable = (Saveable)event.getOldValue();
				Saveable newSaveable = (Saveable)event.getNewValue();
				ISaveablesLifecycleListener listener = (ISaveablesLifecycleListener)getSite().getPage().getWorkbenchWindow()
					.getService(ISaveablesLifecycleListener.class);
				if (listener != null && oldSaveable != null)
					listener.handleLifecycleEvent(new SaveablesLifecycleEvent(this, SaveablesLifecycleEvent.POST_CLOSE, new Saveable[] { oldSaveable }, false));
				if (listener != null && newSaveable != null)
					listener.handleLifecycleEvent(new SaveablesLifecycleEvent(this, SaveablesLifecycleEvent.POST_OPEN, new Saveable[] { newSaveable }, false));
			} else if (event.getProperty().equals(ISynchronizeParticipant.P_CONTENT)) {
				final IWorkbenchSiteProgressService ps = (IWorkbenchSiteProgressService)getSite().getAdapter(IWorkbenchSiteProgressService.class);
				if (ps != null)
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							ps.warnOfContentChange();
						}
					});
			}
		}
		if (source instanceof ISynchronizePageConfiguration) {
			ISynchronizePageConfiguration configuration = (ISynchronizePageConfiguration) source;
			if (event.getProperty().equals(ISynchronizePageConfiguration.P_PAGE_DESCRIPTION)) {
				if (configuration.getParticipant().equals(getParticipant())) {
					updateTitle();
				}
			} else if (event.getProperty().equals(ISynchronizePageConfiguration.P_MODE)) {
				if (configuration.getParticipant().equals(getParticipant())) {
					updateTitle();
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPartListener#partClosed(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partClosed(IWorkbenchPart part) {
		super.partClosed(part);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeView#getParticipant()
	 */
	public ISynchronizeParticipant getParticipant() {
		return activeParticipantRef;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#showPageRec(org.eclipse.ui.part.PageBookView.PageRec)
	 */
	protected void showPageRec(PageRec pageRec) {
		super.showPageRec(pageRec);
		activeParticipantRef = (ISynchronizeParticipant)fPartToParticipant.get(pageRec.part);
		updateActionEnablements();
		updateTitle();		
	}

	/*
	 * Updates the view title based on the active participant
	 */
	protected void updateTitle() {
		ISynchronizeParticipant participant = getParticipant();
		if (participant == null) {
			setContentDescription(""); //$NON-NLS-1$
		} else {
			SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(participant);
			ISynchronizePageConfiguration configuration = part.getConfiguration();
			String description = (String)configuration.getProperty(ISynchronizePageConfiguration.P_PAGE_DESCRIPTION);
			if (description == null)
				description = part.getParticipant().getName();
			// TODO: Get the description from the configuration
			// TODO: listen to the configuration for description changes
			setContentDescription(Utils.shortenText(MAX_NAME_LENGTH, description)); 
			setStatusLineMessage(description, configuration.getMode());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#doDestroyPage(org.eclipse.ui.IWorkbenchPart, org.eclipse.ui.part.PageBookView.PageRec)
	 */
	protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord) {
		IPage page = pageRecord.page;
		page.dispose();
		pageRecord.dispose();
		SynchronizeViewWorkbenchPart syncPart = (SynchronizeViewWorkbenchPart) part;
		ISynchronizeParticipant participant = syncPart.getParticipant();
		clearCrossReferenceCache(part, participant);
	}

	private void clearCrossReferenceCache(IWorkbenchPart part, ISynchronizeParticipant participant) {
		participant.removePropertyChangeListener(this);
		if (part == null)
			return;	
		ISynchronizePageConfiguration configuration = ((SynchronizeViewWorkbenchPart)part).getConfiguration();
		if (configuration != null)
			configuration.removePropertyChangeListener(this);
		fPartToParticipant.remove(part);
		fParticipantToPart.remove(participant);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#doCreatePage(org.eclipse.ui.IWorkbenchPart)
	 */
	protected PageRec doCreatePage(IWorkbenchPart dummyPart) {
		SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)dummyPart;
		ISynchronizeParticipant participant = part.getParticipant();	
		participant.addPropertyChangeListener(this);
		ISynchronizePageConfiguration configuration = participant.createPageConfiguration();
		part.setConfiguration(configuration);
		configuration.addPropertyChangeListener(this);
		IPageBookViewPage page = participant.createPage(configuration);
		if(page != null) {
			initPage(page);
			initPage(configuration, page);
			page.createControl(getPageBook());
			PageRec rec = new PageRec(dummyPart, page);
			return rec;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#initPage(org.eclipse.ui.part.IPageBookViewPage)
	 */
	protected void initPage(ISynchronizePageConfiguration configuration, IPageBookViewPage page) {
		// A page site does not provide everything the page may need
		// Also provide the synchronize page site if the page is a synchronize view page
		((SynchronizePageConfiguration)configuration).setSite(new WorkbenchPartSynchronizePageSite(this, page.getSite(), getDialogSettings(configuration.getParticipant())));
		if (page instanceof ISynchronizePage) {
			try {
				((ISynchronizePage)page).init(configuration.getSite());
			} catch (PartInitException e) {
				TeamUIPlugin.log(IStatus.ERROR, e.getMessage(), e);
			}
		}
		page.getSite().getActionBars().setGlobalActionHandler(ActionFactory.REFRESH.getId(), fPageDropDown);
		page.getSite().getActionBars().updateActionBars();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#isImportant(org.eclipse.ui.IWorkbenchPart)
	 */
	protected boolean isImportant(IWorkbenchPart part) {
		return part instanceof SynchronizeViewWorkbenchPart;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		super.dispose();
		TeamUI.getSynchronizeManager().removeSynchronizeParticipantListener(this);
		// Pin action is hooked up to listeners, must call dispose to un-register.
		fPinAction.dispose();
		// Remember the last active participant
		if(activeParticipantRef != null) {
			rememberCurrentParticipant();
		}			
		fParticipantToPart = null;
		fPartToParticipant = null;	
	}

    /**
     * 
     */
    private void rememberCurrentParticipant() {
        IDialogSettings section = getDialogSettings();
        section.put(KEY_LAST_ACTIVE_PARTICIPANT_ID, activeParticipantRef.getId());
        section.put(KEY_LAST_ACTIVE_PARTICIPANT_SECONDARY_ID, activeParticipantRef.getSecondaryId());
    }

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#createDefaultPage(org.eclipse.ui.part.PageBook)
	 */
	protected IPage createDefaultPage(PageBook book) {
		Page page = new MessagePage();
		page.createControl(getPageBook());
		initPage(page);
		return page;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeParticipantListener#participantsAdded(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public void participantsAdded(final ISynchronizeParticipant[] participants) {
		for (int i = 0; i < participants.length; i++) {
			ISynchronizeParticipant participant = participants[i];
			if (isAvailable() && select(TeamUI.getSynchronizeManager().get(participant.getId(), participant.getSecondaryId()))) {
				SynchronizeViewWorkbenchPart part = new SynchronizeViewWorkbenchPart(participant, getSite());
				fParticipantToPart.put(participant, part);
				fPartToParticipant.put(part, participant);
			}
		}
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				firePropertyChange(PROP_DIRTY);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.sync.ISynchronizeParticipantListener#participantsRemoved(org.eclipse.team.ui.sync.ISynchronizeParticipant[])
	 */
	public void participantsRemoved(final ISynchronizeParticipant[] participants) {
		if (isAvailable()) {
			Runnable r = new Runnable() {
				public void run() {
					for (int i = 0; i < participants.length; i++) {
						ISynchronizeParticipant participant = participants[i];
						if (isAvailable()) {
							SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(participant);
							if (part != null) {
								partClosed(part);
								clearCrossReferenceCache(part, participant);
							}
							// Remove any settings created for the participant
							removeDialogSettings(participant);
							if (getParticipant() == null) {
								ISynchronizeParticipantReference[] available = TeamUI.getSynchronizeManager().getSynchronizeParticipants();
								if (available.length > 0) {
									ISynchronizeParticipant p;
									try {
										p = available[available.length - 1].getParticipant();
									} catch (TeamException e) {
										return;
									}
									display(p);
								}
							}
						}
					}
					firePropertyChange(PROP_DIRTY);
				}
			};
			asyncExec(r);
		}
	}

	/**
	 * Constructs a synchronize view
	 */
	public SynchronizeView() {
		super();
		fParticipantToPart = new HashMap();
		fPartToParticipant = new HashMap();
		updateTitle();
	}
	
	/**
	 * Create the default actions for the view. These will be shown regardless of the
	 * participant being displayed.
	 */
	protected void createActions() {
		fPageDropDown = new SynchronizePageDropDownAction(this);
		fPinAction = new PinParticipantAction();
		fToggleLinkingAction = new ToggleLinkingAction(this);
		fRemoveCurrentAction = new RemoveSynchronizeParticipantAction(this, false);
		fRemoveAllAction = new RemoveSynchronizeParticipantAction(this, true);
		updateActionEnablements();
	}

	private void updateActionEnablements() {
		if (fPinAction != null) {
			fPinAction.setParticipant(activeParticipantRef);
		}
		if (fToggleLinkingAction != null) {
			fToggleLinkingAction.setEnabled(getParticipant() != null);
		}
		if (fRemoveAllAction != null) {
			fRemoveAllAction.setEnabled(getParticipant() != null);
		}
		if (fRemoveCurrentAction != null) {
			fRemoveCurrentAction.setEnabled(getParticipant() != null);
		}
	}

	/**
	 * Add the actions to the toolbar
	 * 
	 * @param bars the action bars
	 */
	protected void configureToolBar(IActionBars bars) {
		IToolBarManager mgr = bars.getToolBarManager();
		mgr.add(fPageDropDown);
		mgr.add(fPinAction);
		IMenuManager menu = bars.getMenuManager();
		menu.add(fPinAction);
		menu.add(fToggleLinkingAction);
		menu.add(fRemoveCurrentAction);
		menu.add(fRemoveAllAction);

		IHandlerService handlerService= (IHandlerService) this.getViewSite().getService(IHandlerService.class);
		handlerService.activateHandler(IWorkbenchCommandConstants.NAVIGATE_TOGGLE_LINK_WITH_EDITOR, new ActionHandler(fToggleLinkingAction));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.ISynchronizeView#display(org.eclipse.team.ui.synchronize.ISynchronizeParticipant)
	 */
	public void display(ISynchronizeParticipant participant) {
		SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart)fParticipantToPart.get(participant);
		if (part != null) {
			partActivated(part);
			fPageDropDown.update();
			createOpenAndLinkWithEditorHelper(getViewer());
            rememberCurrentParticipant();
            PlatformUI.getWorkbench().getHelpSystem().setHelp(getPageBook().getParent(), participant.getHelpContextId());
		}
	}
	
	private void createOpenAndLinkWithEditorHelper(StructuredViewer viewer) {
		if (fOpenAndLinkWithEditorHelper != null)
			fOpenAndLinkWithEditorHelper.dispose();
		fOpenAndLinkWithEditorHelper= new OpenAndLinkWithEditorHelper(viewer) {
			protected void activate(ISelection selection) {
				try {
					final Object selectedElement = getSingleElement(selection);
					if (isOpenInEditor(selectedElement) != null)
						if (selectedElement instanceof IFile)
							openInEditor((IFile) selectedElement, true);
				} catch (PartInitException ex) {
					// ignore if no editor input can be found
				}
			}

			protected void linkToEditor(ISelection selection) {
				SynchronizeView.this.linkToEditor(selection);
			}

			protected void open(ISelection selection, boolean activate) {
				// TODO: implement, bug 291211
			}
		};
		fOpenAndLinkWithEditorHelper.setLinkWithEditor(isLinkingEnabled());
		setLinkingEnabled(isLinkingEnabled());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.PageBookView#getBootstrapPart()
	 */
	protected IWorkbenchPart getBootstrapPart() {
		return null;
	}
	
	/**
	 * Registers the given runnable with the display
	 * associated with this view's control, if any.
	 * @param runnable a runnable
	 */
	public void asyncExec(Runnable runnable) {
		if (isAvailable()) {
			getPageBook().getDisplay().asyncExec(runnable);
		}
	}
	
	/**
	 * Creates this view's underlying viewer and actions.
	 * Hooks a pop-up menu to the underlying viewer's control,
	 * as well as a key listener. When the delete key is pressed,
	 * the <code>REMOVE_ACTION</code> is invoked. Hooks help to
	 * this view. Subclasses must implement the following methods
	 * which are called in the following order when a view is
	 * created:<ul>
	 * <li><code>createViewer(Composite)</code> - the context
	 *   menu is hooked to the viewer's control.</li>
	 * <li><code>createActions()</code></li>
	 * <li><code>configureToolBar(IToolBarManager)</code></li>
	 * <li><code>getHelpContextId()</code></li>
	 * </ul>
	 * @see IWorkbenchPart#createPartControl(Composite)
	 */
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		restoreLinkingEnabled();
		createActions();
		configureToolBar(getViewSite().getActionBars());
		updateForExistingParticipants();
		getViewSite().getActionBars().updateActionBars();
		updateTitle();
		
		IWorkbenchSiteProgressService progress = (IWorkbenchSiteProgressService)getSite().getAdapter(IWorkbenchSiteProgressService.class);
		if(progress != null) {
			progress.showBusyForFamily(ISynchronizeManager.FAMILY_SYNCHRONIZE_OPERATION);
		}
		
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IHelpContextIds.SYNC_VIEW);
	}
	
	/**
	 * Initialize for existing participants
	 */
	private void updateForExistingParticipants() {
		ISynchronizeManager manager = TeamUI.getSynchronizeManager();
		List participants = Arrays.asList(getParticipants());
		boolean errorOccurred = false;
		for (int i = 0; i < participants.size(); i++) {
			try {
				ISynchronizeParticipantReference ref = (ISynchronizeParticipantReference)participants.get(i);
				participantsAdded(new ISynchronizeParticipant[] {ref.getParticipant()});
			} catch (TeamException e) {
				errorOccurred = true;
				continue;
			}
			
		}
		if (errorOccurred) {
			participants = Arrays.asList(getParticipants());
		}
		try {
			// decide which participant to show	on startup
			if (participants.size() > 0) {
				ISynchronizeParticipantReference participantToSelect = (ISynchronizeParticipantReference)participants.get(0);
				IDialogSettings section = getDialogSettings();
				String selectedParticipantId = section.get(KEY_LAST_ACTIVE_PARTICIPANT_ID);
				String selectedParticipantSecId = section.get(KEY_LAST_ACTIVE_PARTICIPANT_SECONDARY_ID);
				if(selectedParticipantId != null) {
					ISynchronizeParticipantReference selectedParticipant = manager.get(selectedParticipantId, selectedParticipantSecId);
					if(selectedParticipant != null) {
						participantToSelect = selectedParticipant;
					}
				}
				display(participantToSelect.getParticipant());
			}
			
			// add as a listener to update when new participants are added
			manager.addSynchronizeParticipantListener(this);
		} catch (TeamException e) {
			Utils.handle(e);
		}
	}
	
	private ISynchronizeParticipantReference[] getParticipants() {
		ISynchronizeManager manager = TeamUI.getSynchronizeManager();
		// create pages
		List participants = new ArrayList();
		ISynchronizeParticipantReference[] refs = manager.getSynchronizeParticipants();
		for (int i = 0; i < refs.length; i++) {
			ISynchronizeParticipantReference ref =refs[i];
			if(select(ref)) {
				participants.add(ref);
			}
		}
		return (ISynchronizeParticipantReference[]) participants.toArray(new ISynchronizeParticipantReference[participants.size()]);
	}
	
	private boolean isAvailable() {
		return getPageBook() != null && !getPageBook().isDisposed();
	}
	
	/*
	 * Method used by test cases to access the page for a participant
	 */
	public IPage getPage(ISynchronizeParticipant participant) {
		IWorkbenchPart part = (IWorkbenchPart)fParticipantToPart.get(participant);
		if (part == null) return null;
		try {
			return getPageRec(part).page;
		} catch (NullPointerException e) {
			// The PageRec class is not visible so we can't do a null check
			// before accessing the page.
			return null;
		}
	}
	
	protected boolean select(ISynchronizeParticipantReference ref) {
		return true;
	}
	
	/*
	 * Return the dialog settings for the view
	 */
	private IDialogSettings getDialogSettings() {
		IDialogSettings workbenchSettings = TeamUIPlugin.getPlugin().getDialogSettings();
		IDialogSettings syncViewSettings = workbenchSettings.getSection(KEY_SETTINGS_SECTION); 
		if (syncViewSettings == null) {
			syncViewSettings = workbenchSettings.addNewSection(KEY_SETTINGS_SECTION);
		}
		return syncViewSettings;
	}
	
	private String getSettingsKey(ISynchronizeParticipant participant) {
		String id = participant.getId();
		String secondaryId = participant.getSecondaryId();
	    return secondaryId == null ? id : id + '.' + secondaryId;
	}
	
	private IDialogSettings getDialogSettings(ISynchronizeParticipant participant) {
		String key = getSettingsKey(participant);
		IDialogSettings viewsSettings = getDialogSettings();
		IDialogSettings settings = viewsSettings.getSection(key);
		if (settings == null) {
			settings = viewsSettings.addNewSection(key);
		}
		return settings;
	}
	
	private void removeDialogSettings(ISynchronizeParticipant participant) {
		String key = getSettingsKey(participant);
		IDialogSettings settings = getDialogSettings();
		if (settings.getSection(key) != null) {
			// There isn't an explicit remove so just make sure
			// That the old settings are forgotten
			getDialogSettings().addSection(new DialogSettings(key));
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablesSource#getSaveables()
	 */
	public Saveable[] getSaveables() {
		Set result = new HashSet();
		for (Iterator iter = fPartToParticipant.keySet().iterator(); iter.hasNext();) {
			SynchronizeViewWorkbenchPart part = (SynchronizeViewWorkbenchPart) iter.next();
			Saveable saveable = getSaveable(part.getParticipant());
			if (saveable != null) {
				result.add(saveable);
			}
		}
		return (Saveable[]) result.toArray(new Saveable[result.size()]);
	}

	private Saveable getSaveable(ISynchronizeParticipant participant) {
		if (participant instanceof ModelSynchronizeParticipant) {
			ModelSynchronizeParticipant msp = (ModelSynchronizeParticipant) participant;
			return msp.getActiveSaveable();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablesSource#getActiveSaveables()
	 */
	public Saveable[] getActiveSaveables() {
		ISynchronizeParticipant participant = getParticipant();
		Saveable s = getSaveable(participant);
		if (s != null)
			return new Saveable[] { s };
		return new Saveable[0];
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablePart#doSave(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void doSave(IProgressMonitor monitor) {
		Saveable[] saveables = getSaveables();
		if (saveables.length == 0)
			return;
		monitor.beginTask(null, 100* saveables.length);
		for (int i = 0; i < saveables.length; i++) {
			Saveable saveable = saveables[i];
			try {
				saveable.doSave(Policy.subMonitorFor(monitor, 100));
			} catch (CoreException e) {
				ErrorDialog.openError(getSite().getShell(), null, e.getMessage(), e.getStatus());
			}
			Policy.checkCanceled(monitor);
		}
		monitor.done();
		firePropertyChange(PROP_DIRTY);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablePart#doSaveAs()
	 */
	public void doSaveAs() {
		// Not allowed
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablePart#isDirty()
	 */
	public boolean isDirty() {
		Saveable[] saveables = getSaveables();
		for (int i = 0; i < saveables.length; i++) {
			Saveable saveable = saveables[i];
			if (saveable.isDirty())
				return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablePart#isSaveAsAllowed()
	 */
	public boolean isSaveAsAllowed() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.ISaveablePart#isSaveOnCloseNeeded()
	 */
	public boolean isSaveOnCloseNeeded() {
		return true;
	}
	
	private void setStatusLineMessage(String description, int mode) {
		String syncMode = null;
		ResourceBundle bundle = Policy.getActionBundle();
		switch (mode) {
		case ISynchronizePageConfiguration.INCOMING_MODE:
			syncMode = Utils.getString("action.directionFilterIncoming.tooltip", bundle); //$NON-NLS-1$
			break;
		case ISynchronizePageConfiguration.OUTGOING_MODE:
			syncMode = Utils.getString("action.directionFilterOutgoing.tooltip", bundle); //$NON-NLS-1$
			break;
		case ISynchronizePageConfiguration.BOTH_MODE:
			syncMode = Utils.getString("action.directionFilterBoth.tooltip", bundle); //$NON-NLS-1$
			break;
		case ISynchronizePageConfiguration.CONFLICTING_MODE:
			syncMode = Utils.getString("action.directionFilterConflicts.tooltip", bundle); //$NON-NLS-1$
			break;			
		}
		
		IViewSite viewSite = getViewSite();
		if (viewSite != null && syncMode != null) {
			viewSite.getActionBars().getStatusLineManager().setMessage(
					NLS.bind(TeamUIMessages.SynchronizeView_statusLine,
							new String[] { description, syncMode }));
		}
	}

	// copy-pasted from org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart and modified

	private IPartListener2 fLinkWithEditorListener= new IPartListener2() {
		public void partVisible(IWorkbenchPartReference partRef) {}
		public void partBroughtToTop(IWorkbenchPartReference partRef) {}
		public void partClosed(IWorkbenchPartReference partRef) {}
		public void partDeactivated(IWorkbenchPartReference partRef) {}
		public void partHidden(IWorkbenchPartReference partRef) {}
		public void partOpened(IWorkbenchPartReference partRef) {}
		public void partInputChanged(IWorkbenchPartReference partRef) {
			if (partRef instanceof IEditorReference) {
				editorActivated(((IEditorReference) partRef).getEditor(true));
			}
		}

		public void partActivated(IWorkbenchPartReference partRef) {
			if (partRef instanceof IEditorReference) {
				editorActivated(((IEditorReference) partRef).getEditor(true));
			}
		}

	};

	public boolean isLinkingEnabled() {
		return fLinkingEnabled;
	}

	private static IElementComparer COMPARER = new IElementComparer() {
		
		private Object getContributedResourceOrResourceVariant(Object o) {
			IResource[] resources = Utils.getContributedResources(new Object[] {o});
			if (resources.length>0 && resources[0].exists())
				return resources[0];
			if (o instanceof SyncInfoModelElement) {
				SyncInfoModelElement sime = (SyncInfoModelElement) o;
				return sime.getSyncInfo().getRemote();
			}
			return null;
		}
		
		public int hashCode(Object element) {
			Object r = getContributedResourceOrResourceVariant(element);
			if (r != null)
				return r.hashCode();
			return element.hashCode();
		}

		public boolean equals(Object a, Object b) {
			// no need to check for null, CustomeHashtable cannot contain null keys
			if (a instanceof IResource || a instanceof IResourceVariant) {
				b = getContributedResourceOrResourceVariant(b);
			} else if (b instanceof IResource || b instanceof IResourceVariant) {
				a = getContributedResourceOrResourceVariant(a);
				return b.equals(a); // a may be null
			}
			return a.equals(b);
		}
	};
	
	public void setLinkingEnabled(boolean enabled) {
		fLinkingEnabled= enabled;
		IDialogSettings dialogSettings = getDialogSettings();
		dialogSettings.put(KEY_LINK_WITH_EDITOR, fLinkingEnabled);

		IWorkbenchPage page= getSite().getPage();
		if (enabled) {
			page.addPartListener(fLinkWithEditorListener);

			IEditorPart editor = page.getActiveEditor();
			if (editor != null)
				editorActivated(editor);
		} else {
			page.removePartListener(fLinkWithEditorListener);
		}
		fOpenAndLinkWithEditorHelper.setLinkWithEditor(enabled);
		
	}

	private void restoreLinkingEnabled() {
		fLinkingEnabled = getDialogSettings().getBoolean(KEY_LINK_WITH_EDITOR);
	}

	/**
	 * Links to editor (if option enabled)
	 * @param selection the selection
	 */
	private void linkToEditor(ISelection selection) {
		Object obj = getSingleElement(selection);
		if (obj != null) {
			IEditorPart part = isOpenInEditor(obj);
			if (part != null) {
				IWorkbenchPage page= getSite().getPage();
				page.bringToTop(part);
			}
		}
	}

	/**
	 * An editor has been activated.  Set the selection in this Packages Viewer
	 * to be the editor's input, if linking is enabled.
	 * @param editor the activated editor
	 */
	private void editorActivated(IEditorPart editor) {
        if (!isLinkingEnabled())
            return;
		
		IEditorInput editorInput= editor.getEditorInput();
		if (editorInput == null)
			return;
		Object input= getInputFromEditor(editorInput);
		if (input == null)
			return;
		if (!inputIsSelected(editorInput))
			showInput(input);
		else
			getViewer().getTree().showSelection();
	}

	boolean showInput(Object input) {
		Object element = input;
		if (element != null) {
			IElementComparer previousComparer = getViewer().getComparer();
			getViewer().setComparer(COMPARER);
			try {
				ISelection newSelection = new StructuredSelection(element);
				if (getViewer().getSelection().equals(newSelection)) {
					getViewer().reveal(element);
				} else {
					getViewer().setSelection(newSelection, true);

					while (element != null && getViewer().getSelection().isEmpty()) {
						// Try to select parent in case element is filtered
						element = getParent(element);
						if (element != null) {
							newSelection = new StructuredSelection(element);
							getViewer().setSelection(newSelection, true);
						} else {
							// Failed to find parent to select
							return false;
						}
					}
				}
				return true;
			} finally {
				getViewer().setComparer(previousComparer);
			}
		}
		return false;
	}

	/**
	 * Returns the element's parent.
	 * @param element the element
	 *
	 * @return the parent or <code>null</code> if there's no parent
	 */
	private Object getParent(Object element) {
		if (element instanceof IResource)
			return ((IResource)element).getParent();
		return null;
	}

	private TreeViewer getViewer() {
		IPage currentPage = getCurrentPage();
		if (currentPage instanceof ISynchronizePage) {
			return (TreeViewer) ((ISynchronizePage)currentPage).getViewer();
		}
		// TODO: nobody is expecting null!
		return null;
	}

	private boolean inputIsSelected(IEditorInput input) {
		IStructuredSelection selection= (IStructuredSelection) getViewer().getSelection();
		if (selection.size() != 1)
			return false;
		IEditorInput selectionAsInput= getEditorInput(selection.getFirstElement());
		return input.equals(selectionAsInput);
	}

	private static IEditorInput getEditorInput(Object input) {
		IResource[] resources = Utils.getContributedResources(new Object[] { input });
		if (resources.length > 0)
			input = resources[0];
		if (input instanceof IFile)
			return new FileEditorInput((IFile) input);
		return null;
	}

	private Object getInputFromEditor(IEditorInput editorInput) {
		Object input= editorInput.getAdapter(IFile.class);
		if (input == null && editorInput instanceof FileRevisionEditorInput) {
			IFileRevision fileRevision = ((FileRevisionEditorInput)editorInput).getFileRevision();
			if (fileRevision instanceof ResourceVariantFileRevision)
				return ((ResourceVariantFileRevision) fileRevision).getVariant();
		}
		if (input == null && editorInput instanceof IStorageEditorInput) {
			try {
				input= ((IStorageEditorInput) editorInput).getStorage();
			} catch (CoreException e) {
				// ignore
			}
		}
		return input;
	}

	// copy-pasted from org.eclipse.jdt.internal.ui.javaeditor.EditorUtility and modified

	private static IEditorPart isOpenInEditor(Object inputElement) {
		IEditorInput input= getEditorInput(inputElement);

		if (input != null) {
			IWorkbenchPage p= TeamUIPlugin.getActivePage();
			if (p != null) {
				return p.findEditor(input);
			}
		}
		return null;
	}

	private static IEditorPart openInEditor(IFile file, boolean activate) throws PartInitException {
		if (file == null)
			throwPartInitException(TeamUIMessages.SynchronizeView_fileMustNotBeNull);

		IWorkbenchPage p = TeamUIPlugin.getActivePage();
		if (p == null)
			throwPartInitException(TeamUIMessages.SynchronizeView_noActiveWorkbenchPage);

		IEditorPart editorPart = IDE.openEditor(p, file, activate);
		return editorPart;
	}

	private static void throwPartInitException(String message) throws PartInitException {
		IStatus status = new Status(IStatus.ERROR, TeamUIPlugin.ID, IStatus.OK, message, null);
		throw new PartInitException(status);
	}

	// copy-pasted from org.eclipse.jdt.internal.ui.util.SelectionUtil and modified

	/**
	 * Returns the selected element if the selection consists of a single
	 * element only.
	 *
	 * @param s the selection
	 * @return the selected first element or null
	 */
	private static Object getSingleElement(ISelection s) {
		if (!(s instanceof IStructuredSelection))
			return null;
		IStructuredSelection selection = (IStructuredSelection) s;
		if (selection.size() != 1)
			return null;

		return selection.getFirstElement();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.IShowInTarget#show(org.eclipse.ui.part.ShowInContext)
	 */
	public boolean show(ShowInContext context) {
		Object selection = getSingleElement(context.getSelection());
		if (selection != null) {
			// If can show the selection, do it.
			// Otherwise, fall through and attempt to show the input
			if (showInput(selection))
				return true;
		}
		Object input = context.getInput();
		if (input != null) {
			if (input instanceof IEditorInput) {
				return showInput(getInputFromEditor((IEditorInput) input));
			}
			 return showInput(input);
		}
		return false;
	}
}