/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2014 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): Victor Perez www.e-evolution.com                           *
 *****************************************************************************/

package org.eevolution.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.compiere.util.CCache;

/**
 * Created by victor.perez@e-evolution.com, e-Evolution on 03/12/13.
 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 *		<a href="https://github.com/adempiere/adempiere/issues/1870>
 * 		@see FR [ 1870 ] Add Calulation for Attendance Record</a>
 */
public class MHRWorkShift extends X_HR_WorkShift {

	public MHRWorkShift(Properties ctx, int HR_WorkShift_ID, String trxName) {
        super(ctx, HR_WorkShift_ID, trxName);
    }

    public MHRWorkShift(Properties ctx, ResultSet rs, String trxName) {
        super(ctx, rs, trxName);
    }
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 955746318164528261L;
    
	/** Cache */
	private static CCache<Integer, MHRWorkShift> workShiftCache = new CCache<Integer, MHRWorkShift>(Table_Name, 1000);
	
	/**
	 * Get Work Shift by Id
	 * @param ctx
	 * @param workShiftId
	 * @return
	 */
	public static MHRWorkShift getById(Properties ctx, int workShiftId) {
		if (workShiftId <= 0)
			return null;

		MHRWorkShift workShift = workShiftCache.get(workShiftId);
		if (workShift != null)
			return workShift;

		workShift = new MHRWorkShift(ctx, workShiftId, null);
		if (workShift.get_ID() == workShiftId)
			workShiftCache.put(workShiftId, workShift);
		else
			workShift = null;
		return workShift;
	}

	@Override
	public String toString() {
		return "MHRWorkShift [getBreakEndTime()=" + getBreakEndTime() + ", getBreakHoursNo()=" + getBreakHoursNo()
				+ ", getBreakStartTime()=" + getBreakStartTime() + ", isOverTimeApplicable()=" + isOverTimeApplicable()
				+ ", getName()=" + getName() + ", getNoOfHours()=" + getNoOfHours() + ", isOnFriday()=" + isOnFriday()
				+ ", isOnMonday()=" + isOnMonday() + ", isOnSaturday()=" + isOnSaturday() + ", isOnSunday()="
				+ isOnSunday() + ", isOnThursday()=" + isOnThursday() + ", isOnTuesday()=" + isOnTuesday()
				+ ", isOnWednesday()=" + isOnWednesday() + ", getShiftFromTime()=" + getShiftFromTime()
				+ ", getShiftToTime()=" + getShiftToTime() + "]";
	}
}
