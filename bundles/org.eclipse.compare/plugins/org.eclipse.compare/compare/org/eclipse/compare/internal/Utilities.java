/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 2000, 2001
 */
package org.eclipse.compare.internal;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.MissingResourceException;

import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.util.*;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.compare.CompareConfiguration;

/**
 * Convenience and utility methods.
 */
public class Utilities {
	
	public static boolean getBoolean(CompareConfiguration cc, String key, boolean dflt) {
		if (cc != null) {
			Object value= cc.getProperty(key);
			if (value instanceof Boolean)
				return ((Boolean) value).booleanValue();
		}
		return dflt;
	}
	
	/**
	 * Retrieves the value from a property change event as a boolean.
	 */
	public static boolean getValue(PropertyChangeEvent event, boolean dflt) {
		Object newValue= event.getNewValue();
		if (newValue instanceof Boolean)
			return ((Boolean)newValue).booleanValue();
		return dflt;
	}
	
	public static void firePropertyChange(ListenerList ll, Object source, String property, Object old, Object newValue) {
		if (ll != null) {
			PropertyChangeEvent event= null;
			Object[] listeners= ll.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				IPropertyChangeListener l= (IPropertyChangeListener) listeners[i];
				if (event == null)
					event= new PropertyChangeEvent(source, property, old, newValue);
				l.propertyChange(event);
			}
		}
	}

	public static boolean okToUse(Widget widget) {
		return widget != null && !widget.isDisposed();
	}
	
	public static boolean isMotif() {
		return false;
	}
		
	/**
	 * Returns the elements of the given selection. 
	 * Returns an empty array if the selection is empty or if 
	 * the given selection is not of type <code>IStructuredSelection</code>.
	 *
	 * @param selection the selection
	 * @return the selected elements
	 */
	public static Object[] toArray(ISelection selection) {
		if (!(selection instanceof IStructuredSelection)) {
			return new Object[0];
		}
		IStructuredSelection ss= (IStructuredSelection) selection;
		return ss.toArray();
	}

	/**
	 * Convenience method: extract all <code>IResources</code> from given selection.
	 * Never returns null.
	 */
	public static IResource[] getResources(ISelection selection) {
		
		List tmp= new ArrayList();

		if (selection instanceof IStructuredSelection) {
		
			Object[] s= ((IStructuredSelection)selection).toArray();
				
			for (int i= 0; i < s.length; i++) {
				Object o= s[i];
				if (o instanceof IResource) {
					tmp.add(o);
					continue;
				}
				if (o instanceof IAdaptable) {
					IAdaptable a= (IAdaptable) o;
					Object adapter= a.getAdapter(IResource.class);
					if (adapter instanceof IResource)
						tmp.add(adapter);
					continue;
				}
			}
		}
		IResource[] resourceSelection= new IResource[tmp.size()];
		tmp.toArray(resourceSelection);
		return resourceSelection;
	}

	public static byte[] readBytes(InputStream in) {
		ByteArrayOutputStream bos= new ByteArrayOutputStream();
		try {		
			while (true) {
				int c= in.read();
				if (c == -1)
					break;
				bos.write(c);
			}
					
		} catch (IOException ex) {
			return null;
		
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException x) {
				}
			}
			try {
				bos.close();
			} catch (IOException x) {
			}
		}
		
		return bos.toByteArray();
	}

	/**
	 * Returns null if an error occurred.
	 */
	public static String readString(InputStream is) {
		if (is == null)
			return null;
		BufferedReader reader= null;
		try {
			StringBuffer buffer= new StringBuffer();
			char[] part= new char[2048];
			int read= 0;
			reader= new BufferedReader(new InputStreamReader(is));

			while ((read= reader.read(part)) != -1)
				buffer.append(part, 0, read);
			
			return buffer.toString();
			
		} catch (IOException ex) {
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
				}
			}
		}
		return null;
	}
	
	/**
	 * Initialize the given Action from a ResourceBundle.
	 */
	public static void initAction(IAction a, ResourceBundle bundle, String prefix) {
		
		String labelKey= "label";
		String tooltipKey= "tooltip";
		String imageKey= "image";
		String descriptionKey= "description";
		
		if (prefix != null && prefix.length() > 0) {
			labelKey= prefix + labelKey;
			tooltipKey= prefix + tooltipKey;
			imageKey= prefix + imageKey;
			descriptionKey= prefix + descriptionKey;
		}
		
		a.setText(getString(bundle, labelKey, labelKey));
		a.setToolTipText(getString(bundle, tooltipKey, null));
		a.setDescription(getString(bundle, descriptionKey, null));
		
		String relPath= getString(bundle, imageKey, null);
		if (relPath != null && relPath.trim().length() > 0) {
			ImageDescriptor id= CompareUIPlugin.getImageDescriptor(relPath);
			if (id != null)
				a.setImageDescriptor(id);
		}
	}
		
	public static String getString(ResourceBundle bundle, String key, String dfltValue) {
		
		if (bundle != null) {
			try {
				return bundle.getString(key);
			} catch (MissingResourceException x) {
			}
		}
		return dfltValue;
	}
	
	public static int getInteger(ResourceBundle bundle, String key, int dfltValue) {
		
		if (bundle != null) {
			try {
				String s= bundle.getString(key);
				if (s != null)
					return Integer.parseInt(s);
			} catch (NumberFormatException x) {
			} catch (MissingResourceException x) {
			}
		}
		return dfltValue;
	}

}
