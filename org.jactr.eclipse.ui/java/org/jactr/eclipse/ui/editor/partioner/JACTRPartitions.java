/*
 * Created on Apr 17, 2007 Copyright (C) 2001-5, Anthony Harrison anh23@pitt.edu
 * (jactr.org) This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the License,
 * or (at your option) any later version. This library is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU Lesser General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jactr.eclipse.ui.editor.partioner;

import org.eclipse.jface.text.IDocument;

public interface JACTRPartitions
{
  static public final String   ID             = "__jactr__partitions";

  static public final String   COMMENT        = "__jactr__comment";

  static public final String   IDENTIFIER     = "__jactr__identifier";

  static public final String[] ALL_PARTITIONS = {
      IDocument.DEFAULT_CONTENT_TYPE, COMMENT, IDENTIFIER };
}
