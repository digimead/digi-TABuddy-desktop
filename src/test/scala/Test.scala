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

import org.scalatest.FreeSpec

class GenericDependenciesSpec extends FreeSpec {
  "bundle 'org.digimead.tabuddy.desktop.core' exists" in {
    Class.forName("org.digimead.tabuddy.desktop.core.Core")
  }
  "bundle 'org.digimead.tabuddy.desktop.core.keyring' exists" in {
    Class.forName("org.digimead.tabuddy.desktop.core.keyring.KeyRing")
  }
  "bundle 'org.digimead.tabuddy.desktop.id' exists" in {
    Class.forName("org.digimead.tabuddy.desktop.id.ID")
  }
  "bundle 'org.digimead.tabuddy.desktop.core.ui' exists" in {
    Class.forName("org.digimead.tabuddy.desktop.core.ui.UI")
  }
  "bundle 'org.digimead.tabuddy.desktop.logic' exists" in {
    Class.forName("org.digimead.tabuddy.desktop.logic.Logic")
  }
  "library 'digi-configgy' exists" in {
    Class.forName("org.digimead.configgy.Configgy")
  }
  "library 'digi-lib' exists" in {
    Class.forName("org.digimead.digi.lib.Activator")
  }
  "library 'digi-lib-jfx4swt' exists" in {
    Class.forName("org.digimead.digi.lib.jfx4swt.JFX")
  }
  "library 'digi-lib-util' exists" in {
    Class.forName("org.digimead.digi.lib.util.Util")
  }
  "library 'digi-tabuddy-model' exists" in {
    Class.forName("org.digimead.tabuddy.model.Model")
  }
  "library 'bcmail-jdk15on' exists" in {
    Class.forName("org.bouncycastle.mail.smime.SMIMEUtil")
  }
  "library 'bcpg-jdk15on' exists" in {
    Class.forName("org.bouncycastle.bcpg.BCPGKey")
  }
  "library 'bcpkix-jdk15on' exists" in {
    Class.forName("org.bouncycastle.pkcs.MacDataGenerator")
  }
  "library 'bcprov-jdk15on' exists" in {
    Class.forName("org.bouncycastle.crypto.Mac")
  }
  "library 'fonts-fontawesome' exists" in {
    Class.forName("com.cathive.fonts.fontawesome.FontAwesome")
  }
}
