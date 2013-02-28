/*
 * Copyright (C) 2003-2011 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ecms.upgrade.plugins;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.PropertyType;
import javax.jcr.Session;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.cms.impl.DMSConfiguration;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.nodetype.ExtendedNodeTypeManager;
import org.exoplatform.services.jcr.core.nodetype.NodeTypeValue;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionValue;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * March 25, 2013  
 */
public class UpgradeExoViewNodeTypePlugin extends UpgradeProductPlugin {

  private static final String EXO_HIDE_EXPLORER_PANEL = "exo:hideExplorerPanel";
  private static final String EXO_VIEW = "exo:view";

  private DMSConfiguration dmsConfiguration_;
  private RepositoryService repoService_;
  private static final Log LOG = ExoLogger.getLogger(UpgradeExoViewNodeTypePlugin.class.getName());
  

  public UpgradeExoViewNodeTypePlugin(RepositoryService repoService, DMSConfiguration dmsConfiguration, 
                               InitParams initParams) {
    super(initParams);
    this.repoService_ = repoService;
    this.dmsConfiguration_ = dmsConfiguration;
  }

  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    if (LOG.isInfoEnabled()) {
      LOG.info("Start " + this.getClass().getName() + ".............");
    }
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try {
      //get info
      Session session = sessionProvider.getSession(dmsConfiguration_.getConfig().getSystemWorkspace(),
                                                                                  repoService_.getCurrentRepository());
      ExtendedNodeTypeManager nodeTypeManager = (ExtendedNodeTypeManager)session.getWorkspace().getNodeTypeManager();
      NodeTypeValue exoView = nodeTypeManager.getNodeTypeValue(EXO_VIEW);
      List<PropertyDefinitionValue> propertyDefinitionList = exoView.getDeclaredPropertyDefinitionValues();
      //check if exo:hideExplorerPanel already exists
      boolean propertyExists = false;
      for (PropertyDefinitionValue propertyDefinition : propertyDefinitionList) {
        if (EXO_HIDE_EXPLORER_PANEL.equals(propertyDefinition.getName())) {
          propertyExists = true;
          break;
        }
      }
      //add new property
      if (!propertyExists) {
        List<String> defaultValues = new ArrayList<String>();
        defaultValues.add("true");

        propertyDefinitionList.add(new PropertyDefinitionValue(EXO_HIDE_EXPLORER_PANEL, true, true, 1, false,
                                                    defaultValues, false, PropertyType.BOOLEAN, new ArrayList<String>()));
        exoView.setDeclaredPropertyDefinitionValues(propertyDefinitionList);
        nodeTypeManager.registerNodeType(exoView, ExtendedNodeTypeManager.REPLACE_IF_EXISTS);
      }
      if (LOG.isInfoEnabled()) {
        LOG.info("Add new property '" + EXO_HIDE_EXPLORER_PANEL + "' for node type '" + EXO_VIEW + "' successfully!");
      }
    } catch (Exception e) {
      if (LOG.isErrorEnabled()) {
        LOG.error("An unexpected error occurs when add new property '" + EXO_HIDE_EXPLORER_PANEL
            + "' for node type '" + EXO_VIEW + "'!", e);
      }
    } finally {
      sessionProvider.close();
    }
  }

  @Override
  public boolean shouldProceedToUpgrade(String previousVersion, String newVersion) {
    return true;
  }

}
