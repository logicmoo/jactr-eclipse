package org.jactr.eclipse.core.bundles.meta;

/*
 * default logging
 */
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginImport;
import org.eclipse.pde.core.plugin.IPluginLibrary;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ibundle.IBundle;
import org.eclipse.pde.internal.core.ibundle.IBundlePluginModelBase;
import org.osgi.framework.Constants;

public class ManifestTools
{
  /**
   * Logger definition
   */
  static private final transient Log LOGGER = LogFactory
                                                .getLog(ManifestTools.class);

  /**
   * will attempt to set a header/value pair in the manifest
   * 
   * @param pluginBase
   * @param header
   * @param value
   * @return
   */
  static public boolean addHeader(IPluginModelBase pluginBase, String header,
      String value)
  {
    if (pluginBase instanceof IBundlePluginModelBase)
    {
      IBundle bundle = ((IBundlePluginModelBase) pluginBase).getBundleModel()
          .getBundle();
      bundle.setHeader(header, value);

      return true;
    }
    else
      return false;
  }

  static public String getHeader(IPluginModelBase pluginBase, String header)
  {
    if (pluginBase instanceof IBundlePluginModelBase)
    {
      IBundle bundle = ((IBundlePluginModelBase) pluginBase).getBundleModel()
          .getBundle();
      return bundle.getHeader(header);
    }
    else
      return null;
  }

  static public boolean addEclipseBuddies(IPluginModelBase pluginBase,
      Collection<String> newBuddies)
  {
    Set<String> buddiesToSet = new TreeSet<String>();
    buddiesToSet.addAll(newBuddies);

    String oldBuddies = getHeader(pluginBase, "Eclipse-RegisterBuddy");
    if (oldBuddies != null) for (String buddy : oldBuddies.split(","))
    {
      buddy = buddy.trim();
      if (buddy.length() != 0) buddiesToSet.add(buddy);
    }

    StringBuilder buddies = new StringBuilder();

    for (String buddy : buddiesToSet)
      buddies.append(buddy).append(",\n ");

    buddies.delete(buddies.length() - 3, buddies.length());
    addHeader(pluginBase, "Eclipse-RegisterBuddy", buddies.toString());
    return addHeader(pluginBase, "Eclipse-BuddyPolicy", "registered");
  }
  
  static public boolean addExportPackages(IPluginModelBase pluginBase, Collection<String> newPackages)
  {
    Set<String> packages = new TreeSet<String>();
    packages.addAll(newPackages);

    String oldPackages = getHeader(pluginBase, Constants.EXPORT_PACKAGE);
    
    if (oldPackages != null) for (String pack : oldPackages.split(","))
    {
      pack = pack.trim();
      if (pack.length() != 0) packages.add(pack);
    }

    StringBuilder packageString = new StringBuilder();

    for (String pack : packages)
      packageString.append(pack).append(",\n ");

    packageString.delete(packageString.length() - 3, packageString.length());
    return addHeader(pluginBase, Constants.EXPORT_PACKAGE, packageString.toString());
  }
  
  static public boolean save(IPluginModelBase pluginBase)
  {
    if(pluginBase instanceof IBundlePluginModelBase)
     {
       ((IBundlePluginModelBase)pluginBase).save();
       return true;
     }
    return false;
  }

  /**
   * will try to add plugin references
   * 
   * @param pluginBase
   * @param plugins
   * @throws CoreException
   */
  static public void addPluginReferences(IPluginModelBase pluginBase,
      Collection<String> plugins) throws CoreException
  {
    IPluginBase base = pluginBase.getPluginBase();
    IPluginImport[] imports = base.getImports();
    for (String plugin : plugins)
    {
      /*
       * make sure we dont already have it..
       */
      IPluginImport imp = null;
      for (IPluginImport tmp : imports)
        if (tmp.getId().equals(plugin))
        {
          imp = tmp;
          break;
        }

      if (imp != null) continue;

      imp = pluginBase.getPluginFactory().createImport();
      imp.setId(plugin);
      imp.setVersion(null);
      imp.setMatch(0);
      base.add(imp);
    }
  }
  
  static public void addClassPathReferences(IPluginModelBase pluginBase, Collection<String> paths, String type) throws CoreException
  {
    IPluginBase base = pluginBase.getPluginBase();
    IPluginLibrary[] libs = base.getLibraries();
    for(String path : paths)
    {
      IPluginLibrary lib = null;
      for(IPluginLibrary tmp : libs)
        if(tmp.getName().equals(path))
        {
          lib = tmp;
          break;
        }
      
      if(lib!=null) continue;
      lib = pluginBase.getPluginFactory().createLibrary();
      lib.setType(type);
      lib.setName(path);
      lib.setExported(true);
      base.add(lib);
    }
  }
}
