/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2013-2014 Alexey Aksenov ezh@ezh.msk.ru
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.core

import java.util.ResourceBundle
import org.digimead.digi.lib.log.api.XLoggable
import org.digimead.tabuddy.desktop.core.definition.{ BaseResourceBundle, NLS }

/**
 * Resource bundle implementation.
 *
 * This code is directly evaluated in IDE (WindowBuilderPro).
 * Any runtime references that may prevent creation are prohibited.
 */
class Messages extends ResourceBundle with BaseResourceBundle {
  def getKeys() = new java.util.Enumeration[String] {
    private val iterator = Messages.T.messages.keys.iterator
    def hasMoreElements(): Boolean = iterator.hasNext
    def nextElement(): String = iterator.next()
  }
  protected def handleGetObject(key: String): Object = try {
    Messages.T.messages.get(key).
      getOrElse { Messages.log.error(s"'${key}' not found in ${this.getClass()}"); key }
  } catch {
    case e: Throwable ⇒
      key
  }
}

object Messages extends NLS with XLoggable {
  val TABuddyDesktop = ""
  val acquire_text = ""
  val activate_text = ""
  val activeElement_text = ""
  val activeSchema_text = ""
  val active_text = ""
  val add_text = ""
  val additionalInformation_text = ""
  val alias_text = ""
  val argument_text = ""
  val ascending_text = ""
  val autoresize_key = ""
  val availability_text = ""
  val available_text = ""
  val base_text = ""
  val blocked_text = ""
  val cancelled_text = ""
  val closeFile_text = ""
  val collapseAll_text = ""
  val collapseRecursively_text = ""
  val console_text = ""
  val context_listDescriptionLong_text = ""
  val context_listDescriptionShort_text = ""
  val context_list_text = ""
  val copy_item_text = ""
  val copy_text = ""
  val coreDiscover_text = ""
  val coreModel_text = ""
  val coreModel_tooltip_text = ""
  val coreSelected_text = ""
  val coreSelected_tooltip_text = ""
  val createFrom_text = ""
  val create_text = ""
  val cut_text = ""
  val default_text = ""
  val definition_text = ""
  val delete_text = ""
  val descending_text = ""
  val description_text = ""
  val direction_text = ""
  val down_text = ""
  val edit_text = ""
  val elementEditorDescription_text = ""
  val elementEditorDialog_text = ""
  val elementEditorTitle_text = ""
  val elementTemplateEditorDescription_text = ""
  val elementTemplateEditorDialog_text = ""
  val elementTemplateEditorTitle_text = ""
  val elementTemplateListDescription_text = ""
  val elementTemplateListDialog_text = ""
  val elementTemplateListTitle_text = ""
  val elementTemplates_text = ""
  val elementsPredefined_text = ""
  val emptyRows_text = ""
  val empty_text = ""
  val enumerationEditorDescription_text = ""
  val enumerationEditorDialog_text = ""
  val enumerationEditorTitle_text = ""
  val enumerationListDescription_text = ""
  val enumerationListDialog_text = ""
  val enumerationListTitle_text = ""
  val enumerationTooltip_text = ""
  val enumerationUnableToCreateNoTypes_text = ""
  val enumerationUnableToCreate_text = ""
  val enumerations_text = ""
  val errorReportContactHint_text = ""
  val errorReportContactTip_text = ""
  val errorReportContact_text = ""
  val errorReportDescription_text = ""
  val errorReportDetails_text = ""
  val errorReportReasonDefault_text = ""
  val errorReportTitle_text = ""
  val errorReportUploadAquireFailed_text = ""
  val errorReportUploadAquire_text = ""
  val errorReportUploadCancelled_text = ""
  val errorReportUploadFailed_text = ""
  val errorReportUploadTitle_text = ""
  val error_text = ""
  val exitDescriptionLong_text = ""
  val exitDescriptionShort_text = ""
  val exit_text = ""
  val expandAll_text = ""
  val expandNew_text = ""
  val expandRecursively_text = ""
  val expand_text = ""
  val field_text = ""
  val fields_text = ""
  val filter_text = ""
  val filters_text = ""
  val freeze_text = ""
  val group_text = ""
  val helpDescriptionLong_text = ""
  val helpDescriptionShort_text = ""
  val help_text = ""
  val hidden_text = ""
  val hide_text = ""
  val identificatorCharacterIsNotValid_text = "Invalid character. Vaild only letters of any language, numbers and '_' at the middle or end."
  val identificatorIsAlreadyInUse_text = "Identificator '%s' is already in use."
  val identificatorIsNotDefined_text = "Identificator is not defined."
  val identificator_text = "Identificator"
  val identificators_text = "Identificators"
  val infoDescriptionLong_text = ""
  val infoDescriptionShort_text = ""
  val info_text = ""
  val inversion_text = ""
  val key_text = ""
  val lastModification_text = ""
  val left_text = ""
  val link_text = ""
  val loadFile_text = ""
  val localModel_text = ""
  val localModel_tooltip_text = ""
  val lock_text = ""
  val lookupAliasInTranslations_text = ""
  val lookupFilter_text = ""
  val markAsRoot_text = ""
  val menuEdit_text = ""
  val menuFile_text = ""
  val menuModel_text = ""
  val nameIsAlreadyInUse_text = ""
  val nameIsNotDefined_text = ""
  val name_text = ""
  val newFilterName_text = ""
  val newSortingName_text = ""
  val newTypeSchema_text = ""
  val newValue_text = ""
  val new_text = ""
  val no_text = ""
  val nodata_text = ""
  val openFile_text = ""
  val overViewPanelTitle_text = ""
  val paste_text = ""
  val path_text = ""
  val properties_text = ""
  val property_text = ""
  val reason_text = ""
  val redo_text = ""
  val remove_text = ""
  val required_text = ""
  val resetPredefinedTemplate_text = ""
  val resetSorting_text = ""
  val reset_text = ""
  val right_text = ""
  val rootElement_text = ""
  val saveFile_text = ""
  val select_text = ""
  val settings_text = ""
  val shell_detailed_text = ""
  val shell_text = ""
  val sorting_text = ""
  val sortings_text = ""
  val string_text = ""
  val synchronize_text = ""
  val systemElements_text = ""
  val tableView_text = ""
  val table_text = ""
  val thereAreDuplicatedValuesInField_text = ""
  val thereAreNoSelectedProperties_text = ""
  val thereIsNoData_text = ""
  val translationDialog_text = ""
  val tree_text = ""
  val typeEditorDescription_text = ""
  val typeEditorDialog_text = ""
  val typeEditorTitle_text = ""
  val typeInternalRepresentaion_text = ""
  val typeIsUnknown_text = ""
  val typeListDescription_text = ""
  val typeListDialog_text = ""
  val typeListTitle_text = ""
  val typeSchemaDefaultDescription_text = ""
  val typeSchemaPredefinedResetToDefault_text = ""
  val typeSchemaResetUnknownTypes_text = ""
  val typeSchemaTooltip_text = ""
  val typeSchemas_text = ""
  val type_text = ""
  val types_text = ""
  val undo_text = ""
  val unknown_view_text = ""
  val untitled_text = ""
  val up_text = ""
  val valueCharacterIsNotValid_text = ""
  val valueIsNotDefined_text = ""
  val value_text = ""
  val view_text = ""
  val view_tooltip_text = ""
  val views_text = ""
  val warningMessage_text = "Warning: %s"
  val yes_text = ""

  T.ranslate("org.digimead.tabuddy.desktop.core.messages")
}
