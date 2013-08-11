/**
 * This file is part of the TABuddy project.
 * Copyright (c) 2013 Alexey Aksenov ezh@ezh.msk.ru
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
 * that is created or manipulated using TABuddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TABuddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TABuddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.ui.dialog

import org.digimead.tabuddy.desktop.job.view.JobModifyFilter
import org.digimead.tabuddy.desktop.job.view.JobModifyFilterImplementation
import org.digimead.tabuddy.desktop.job.view.JobModifyFilterList
import org.digimead.tabuddy.desktop.job.view.JobModifyFilterListImplementation
import org.digimead.tabuddy.desktop.job.view.JobModifySorting
import org.digimead.tabuddy.desktop.job.view.JobModifySortingImplementation
import org.digimead.tabuddy.desktop.job.view.JobModifySortingList
import org.digimead.tabuddy.desktop.job.view.JobModifySortingListImplementation
import org.digimead.tabuddy.desktop.job.view.JobModifyView
import org.digimead.tabuddy.desktop.job.view.JobModifyViewImplementation
import org.digimead.tabuddy.desktop.job.view.JobModifyViewList
import org.digimead.tabuddy.desktop.job.view.JobModifyViewListImplementation
import org.digimead.tabuddy.desktop.payload.view.Filter
import org.digimead.tabuddy.desktop.payload.view.Sorting
import org.digimead.tabuddy.desktop.payload.view.View

import com.escalatesoft.subcut.inject.NewBindingModule

package object view {
  lazy val default = new NewBindingModule(module => {
    // JobModifyFilterImplementation
    module.bind[(Filter, Set[Filter], Symbol) => JobModifyFilter] toSingle {
      (filter: Filter, filterList: Set[Filter], modelID: Symbol) => new JobModifyFilterImplementation(filter, filterList, modelID)
    }
    // JobModifyFilterListImplementation
    module.bind[(Set[Filter], Symbol) => JobModifyFilterList] toSingle {
      (fiterList: Set[Filter], modelID: Symbol) => new JobModifyFilterListImplementation(fiterList, modelID)
    }
    // JobModifySortingImplementation
    module.bind[(Sorting, Set[Sorting], Symbol) => JobModifySorting] toSingle {
      (sorting: Sorting, sortingList: Set[Sorting], modelID: Symbol) => new JobModifySortingImplementation(sorting, sortingList, modelID)
    }
    // JobModifySortingListImplementation
    module.bind[(Set[Sorting], Symbol) => JobModifySortingList] toSingle {
      (sortingList: Set[Sorting], modelID: Symbol) => new JobModifySortingListImplementation(sortingList, modelID)
    }
    // JobModifyViewImplementation
    module.bind[(View, Set[View], Symbol) => JobModifyView] toSingle {
      (view: View, viewList: Set[View], modelID: Symbol) => new JobModifyViewImplementation(view, viewList, modelID)
    }
    // JobModifyViewListImplementation
    module.bind[(Set[View], Symbol) => JobModifyViewList] toSingle {
      (viewList: Set[View], modelID: Symbol) => new JobModifyViewListImplementation(viewList, modelID)
    }
  })
}
