/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ecm.webui.component.admin.script;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.version.VersionHistory;

import org.exoplatform.ecm.jcr.model.VersionNode;
import org.exoplatform.ecm.webui.component.admin.script.UIScriptList.ScriptData;
import org.exoplatform.ecm.webui.form.validator.XSSValidator;
import org.exoplatform.ecm.webui.utils.Utils;
import org.exoplatform.services.cms.scripts.ScriptService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPopupComponent;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.core.model.SelectItemOption;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.form.UIForm;
import org.exoplatform.webui.form.UIFormSelectBox;
import org.exoplatform.webui.form.UIFormStringInput;
import org.exoplatform.webui.form.UIFormTextAreaInput;
import org.exoplatform.webui.form.input.UICheckBoxInput;
import org.exoplatform.webui.form.validator.MandatoryValidator;
import org.exoplatform.webui.form.validator.NameValidator;

/**
 * Created by The eXo Platform SARL
 * September 27, 2006 10:27:15 AM
 */

@ComponentConfig(
    lifecycle = UIFormLifecycle.class,
    template = "system:/groovy/webui/form/UIForm.gtmpl",
    events = {
      @EventConfig(listeners = UIScriptForm.SaveActionListener.class),
      @EventConfig(phase=Phase.DECODE, listeners = UIScriptForm.RestoreActionListener.class),
      @EventConfig(phase=Phase.DECODE, listeners = UIScriptForm.CancelActionListener.class),
      @EventConfig(phase=Phase.DECODE, listeners = UIScriptForm.RefreshActionListener.class)
    }
)
public class UIScriptForm extends UIForm implements UIPopupComponent {

  private static final Log LOG = ExoLogger.getLogger(UIScriptForm.class.getName());
  
  final static public String FIELD_SELECT_VERSION = "selectVersion" ;
  final static public String FIELD_SCRIPT_CONTENT = "scriptContent" ;
  final static public String FIELD_SCRIPT_NAME = "scriptName" ;
  final static public String FIELD_SCRIPT_LABEL = "scriptLabel" ;
  final static public String FIELD_ENABLE_VERSION = "enableVersion" ;
  final static public String SCRIPT_FILE_TYPE = ".groovy" ;

  private List<String> listVersion = new ArrayList<String>() ;
  private boolean isAddNew_ = true ;
  private ScriptService scriptService;

  public UIScriptForm() throws Exception {
    scriptService = WCMCoreUtils.getService(ScriptService.class);

    List<SelectItemOption<String>> options = new ArrayList<SelectItemOption<String>>() ;
    UIFormSelectBox versions =
      new UIFormSelectBox(FIELD_SELECT_VERSION , FIELD_SELECT_VERSION, options) ;
    UIFormTextAreaInput contents =
      new UIFormTextAreaInput(FIELD_SCRIPT_CONTENT , FIELD_SCRIPT_CONTENT, null) ;
    contents.addValidator(MandatoryValidator.class) ;
    UICheckBoxInput isVersion = new UICheckBoxInput(FIELD_ENABLE_VERSION , FIELD_ENABLE_VERSION, null) ;
    UIFormStringInput scriptLabel = new UIFormStringInput(FIELD_SCRIPT_LABEL, FIELD_SCRIPT_LABEL, null) ;
    scriptLabel.addValidator(XSSValidator.class);
    UIFormStringInput scriptName = new UIFormStringInput(FIELD_SCRIPT_NAME, FIELD_SCRIPT_NAME, null) ;
    scriptName.addValidator(MandatoryValidator.class).addValidator(NameValidator.class).addValidator(XSSValidator.class);
    versions.setOnChange("Change") ;
    versions.setRendered(false) ;
    isVersion.setRendered(false) ;
    addUIFormInput(versions) ;
    addUIFormInput(contents) ;
    addUIFormInput(isVersion) ;
    addUIFormInput(scriptLabel) ;
    addUIFormInput(scriptName) ;
  }

  private VersionNode getRootVersion(Node node) throws Exception{
    VersionHistory vH = node.getVersionHistory() ;
    return (vH == null) ? null : new VersionNode(vH.getRootVersion(), node.getSession()) ;
  }

  private List<String> getNodeVersions(List<VersionNode> children) throws Exception {
    List<VersionNode> child = new ArrayList<VersionNode>() ;
    for(int i = 0; i < children.size(); i ++){
      listVersion.add(children.get(i).getName());
      child = children.get(i).getChildren() ;
      if(!child.isEmpty()) getNodeVersions(child) ;
    }
    return listVersion ;
  }

  private List<SelectItemOption<String>> getVersionValues(Node node) throws Exception {
    List<SelectItemOption<String>> options = new ArrayList<SelectItemOption<String>>() ;
    List<VersionNode> children = getRootVersion(node).getChildren() ;
    listVersion.clear() ;
    List<String> versionList = getNodeVersions(children) ;
    for(int i = 0; i < versionList.size(); i++) {
      for(int j = i + 1; j < versionList.size(); j ++) {
        if(Integer.parseInt(versionList.get(j)) < Integer.parseInt(versionList.get(i))) {
          String temp = versionList.get(i) ;
          versionList.set(i, versionList.get(j))  ;
          versionList.set(j, temp) ;
        }
      }
      options.add(new SelectItemOption<String>(versionList.get(i), versionList.get(i))) ;
    }
    return options ;
  }

  public void update(Node script, boolean isAddNew) throws Exception{
    isAddNew_ = isAddNew ;
    if(script != null) {
      String scriptContent = scriptService.getScriptAsText(script);
      getUICheckBoxInput(FIELD_ENABLE_VERSION).setRendered(true) ;
      boolean isVersioned = script.isNodeType(Utils.MIX_VERSIONABLE) ;
      if(isVersioned) {
        getUIFormSelectBox(FIELD_SELECT_VERSION).setRendered(true) ;
        getUIFormSelectBox(FIELD_SELECT_VERSION).setOptions(getVersionValues(script)) ;
        getUIFormSelectBox(FIELD_SELECT_VERSION).setValue(script.getBaseVersion().getName()) ;
        getUICheckBoxInput(FIELD_ENABLE_VERSION).setDisabled(true);
        getUICheckBoxInput(FIELD_ENABLE_VERSION).setChecked(true) ;
        setActions(new String[]{"Save", "Restore", "Refresh", "Cancel"})  ;
      } else {
        getUIFormSelectBox(FIELD_SELECT_VERSION).setRendered(false) ;
        getUICheckBoxInput(FIELD_ENABLE_VERSION).setDisabled(false) ;
        getUICheckBoxInput(FIELD_ENABLE_VERSION).setChecked(false) ;
        setActions( new String[]{"Save", "Refresh", "Cancel"}) ;
      }
      getUIFormTextAreaInput(FIELD_SCRIPT_CONTENT).setValue(scriptContent) ;
      Node content = script.getNode(NodetypeConstant.JCR_CONTENT);
      String scriptLabel = content.getProperty(NodetypeConstant.DC_DESCRIPTION).getValues()[0].getString();
      getUIStringInput(FIELD_SCRIPT_LABEL).setValue(scriptLabel) ;
      getUIStringInput(FIELD_SCRIPT_NAME).setValue(script.getName()) ;
      getUIStringInput(FIELD_SCRIPT_NAME).setDisabled(true) ;
      return ;
    }
    if(!isAddNew_) {
      getUIFormTextAreaInput(FIELD_SCRIPT_CONTENT).setValue(null) ;
      return ;
    }
    getUIFormSelectBox(FIELD_SELECT_VERSION).setRendered(false) ;
    getUICheckBoxInput(FIELD_ENABLE_VERSION).setRendered(false) ;
    getUICheckBoxInput(FIELD_ENABLE_VERSION).setChecked(false) ;
    getUIStringInput(FIELD_SCRIPT_LABEL).setValue(null) ;
    getUIStringInput(FIELD_SCRIPT_NAME).setDisabled(false);
    getUIStringInput(FIELD_SCRIPT_NAME).setValue(null) ;
    getUIFormTextAreaInput(FIELD_SCRIPT_CONTENT).setValue(null) ;
    setActions( new String[]{"Save", "Refresh", "Cancel"}) ;
  }

  public void activate() { }
  public void deActivate() { }

  static public class SaveActionListener extends EventListener<UIScriptForm> {
    public void execute(Event<UIScriptForm> event) throws Exception {
      UIScriptForm uiForm = event.getSource() ;
      ScriptService scriptService = uiForm.getApplicationComponent(ScriptService.class) ;
      UIApplication uiApp = uiForm.getAncestorOfType(UIApplication.class) ;
      StringBuffer name = new StringBuffer();
      name.append(uiForm.getUIStringInput(FIELD_SCRIPT_NAME).getValue().trim());
      String content = uiForm.getUIFormTextAreaInput(FIELD_SCRIPT_CONTENT).getValue() ;
      String label = uiForm.getUIStringInput(FIELD_SCRIPT_LABEL).getValue().trim();
      if (content == null)
        content = "";
      if (name == null || name.toString().trim().length() == 0) {
        uiApp.addMessage(new ApplicationMessage("UIScriptForm.msg.name-null",
                                                null,
                                                ApplicationMessage.WARNING));

        return;
      }
      String[] arrFilterChar = {"&", "$", "@", ":","]", "'", "[", "*", "%", "!", "\""};
      for(String filterChar : arrFilterChar) {
        if(name.indexOf(filterChar) > -1) {
          uiApp.addMessage(new ApplicationMessage("UIScriptForm.msg.fileName-invalid", null,
                                                  ApplicationMessage.WARNING)) ;
          
          return ;
        }
      }
      if (name.indexOf(SCRIPT_FILE_TYPE) < 0) {
        name.append(SCRIPT_FILE_TYPE);
      }
      UIScriptList curentList = null ;
      UIScriptManager uiManager = uiForm.getAncestorOfType(UIScriptManager.class) ;
      List<String> listScript = new ArrayList<String>() ;
      List<ScriptData> scriptDatas = new ArrayList<ScriptData>() ;
      String namePrefix = null ;
      
      UIScriptContainer uiContainer = uiManager.getChildById(uiManager.getSelectedTabId());
      curentList = uiContainer.getChild(UIScriptList.class);
      namePrefix = curentList.getScriptCategory() ;
      String subNamePrefix = namePrefix.substring(namePrefix.lastIndexOf("/") + 1, namePrefix.length()) ;
      scriptDatas = curentList.getcript(subNamePrefix) ;
        
        
      for(ScriptData data : scriptDatas) {
        listScript.add(data.getName()) ;
      }
      if(listScript.contains(name.toString()) && uiForm.isAddNew_) {
        Object[] args = { name } ;
        ApplicationMessage appMessage = 
          new ApplicationMessage("UIScriptForm.msg.name-exist", args, ApplicationMessage.WARNING);
        appMessage.setArgsLocalized(false);
        uiApp.addMessage(appMessage) ;        
        return ;
      }
      boolean isEnableVersioning = uiForm.getUICheckBoxInput(FIELD_ENABLE_VERSION).isChecked() ;
      if(uiForm.isAddNew_ || !isEnableVersioning) {
        try {
          scriptService.addScript(namePrefix + "/" + name, label, content, WCMCoreUtils.getUserSessionProvider());
        } catch(AccessDeniedException ace) {
          uiApp.addMessage(new ApplicationMessage("UIECMAdminControlPanel.msg.access-denied", null,
                                                  ApplicationMessage.WARNING)) ;
          
          return ;
        }
      } else {
        try {
          Node node = curentList.getScriptNode(curentList.getTemplateFilter(), name.toString()) ;
          if(!node.isNodeType(Utils.MIX_VERSIONABLE)) node.addMixin(Utils.MIX_VERSIONABLE) ;
          else node.checkout() ;
          scriptService.addScript(namePrefix + "/" + name, label, content, WCMCoreUtils.getUserSessionProvider());
          node.save() ;
          node.checkin() ;
        } catch (PathNotFoundException pathNotFoundException) {
          Object[] args = { namePrefix };
          uiApp.addMessage(new ApplicationMessage("UIScriptForm.msg.PathNotFoundException", args,
              ApplicationMessage.WARNING));
          
          return;
        }
      }
      uiForm.reset() ;      
      UIPopupWindow uiPopup = uiManager.getChild(UIPopupWindow.class);
      uiPopup.setRendered(false);
      event.getRequestContext().addUIComponentToUpdateByAjax(curentList) ;
      curentList.refresh(curentList.getTemplateFilter(),1) ;
      event.getRequestContext().addUIComponentToUpdateByAjax(uiManager) ;
    }
  }

  static public class RestoreActionListener extends EventListener<UIScriptForm> {
    public void execute(Event<UIScriptForm> event) throws Exception {    
      UIScriptForm uiForm = event.getSource();
      String name = uiForm.getUIStringInput(FIELD_SCRIPT_NAME).getValue() ;
      UIScriptList uiScriptList = null ;
      UIScriptManager uiManager = uiForm.getAncestorOfType(UIScriptManager.class) ;    
      UIScriptContainer uiContainer = uiManager.getChildById(uiManager.getSelectedTabId());
      uiScriptList = uiContainer.getChild(UIScriptList.class);
          
      try {
        Node node = uiScriptList.getScriptNode(uiScriptList.getTemplateFilter(), name);
        String vesion = uiForm.getUIFormSelectBox(FIELD_SELECT_VERSION).getValue();
        String baseVesion = node.getBaseVersion().getName() ;
        if(!vesion.equals(baseVesion)) {
          node.checkout() ;
          node.restore(vesion, true) ;
          uiScriptList.refresh(uiScriptList.getTemplateFilter(), 1) ;
        }  
        UIPopupWindow uiPopup = uiManager.getChild(UIPopupWindow.class);
        uiPopup.setRendered(false);
        event.getRequestContext().addUIComponentToUpdateByAjax(uiManager) ;
      } catch (PathNotFoundException pne) {
        if (LOG.isWarnEnabled()) {
          LOG.warn(pne.getMessage());
        }
      }
    }
  } 

  static public class RefreshActionListener extends EventListener<UIScriptForm> {
    public void execute(Event<UIScriptForm> event) throws Exception {
      UIScriptForm uiForm = event.getSource() ;
      String sciptName = uiForm.getUIStringInput(UIScriptForm.FIELD_SCRIPT_NAME).getValue() ;
      if(uiForm.isAddNew_) {
        uiForm.update(null, true) ;
      } else {
        UIScriptManager uiScriptManager = uiForm.getAncestorOfType(UIScriptManager.class);
        UIScriptContainer uiScriptContainer = uiScriptManager.getChildById(uiScriptManager.getSelectedTabId());
        UIScriptList uiScriptList = uiScriptContainer.getChild(UIScriptList.class);        
        try {
          Node script = uiScriptList.getScriptNode(uiScriptList.getTemplateFilter(), sciptName) ;
          uiForm.update(script, false) ;
        } catch (PathNotFoundException pathNotFoundException) {
          String namePrefix = uiScriptList.getScriptCategory();
          Object[] args = { namePrefix };
          UIApplication uiApp = uiForm.getAncestorOfType(UIApplication.class);
          uiApp.addMessage(new ApplicationMessage("UIScriptForm.msg.PathNotFoundException", args,
              ApplicationMessage.WARNING));          
          return;
        }
      }
      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm.getAncestorOfType(UIScriptManager.class)) ;
    }
  }

  static public class CancelActionListener extends EventListener<UIScriptForm> {
    public void execute(Event<UIScriptForm> event) throws Exception {
      UIScriptForm uiForm = event.getSource();      
      uiForm.reset() ;     
      UIScriptManager uiManager = uiForm.getAncestorOfType(UIScriptManager.class) ;
      UIPopupWindow uiPopup = uiManager.getChildById(UIScriptManager.POPUP_TEMPLATE_ID);
      uiPopup.setRendered(false);
      event.getRequestContext().addUIComponentToUpdateByAjax(uiManager) ;
    }
  }
}
