/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
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
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.process;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MProject;
import org.compiere.model.MProjectLine;
import org.compiere.model.MUOMConversion;
import org.compiere.util.Env;

/**
 *  Generate Sales Order from Project.
 *
 *	@author Jorg Janke
 *	@version $Id: ProjectGenOrder.java,v 1.3 2006/07/30 00:51:01 jjanke Exp $
 *	@author Carlos Parada, cparada@erpya.com, ERPCyA http://www.erpya.com
 *   	<a href="https://github.com/adempiere/adempiere/issues/2111">
 *		@see BR [ 2111 ] Save Order Line</a>
 *
 *
 */
public class ProjectGenOrder extends ProjectGenOrderAbstract {

	/**
	 *  Perform process.
	 *  @return Message (clear text)
	 *  @throws Exception if not successful
	 */
	protected String doIt() throws Exception
	{
		log.info("C_Project_ID=" + getRecord_ID());
		if (getRecord_ID() == 0)
			throw new AdempiereException("@C_Project_ID@ @NotFound@");
		MProject fromProject = getProject (getCtx(), getRecord_ID(), get_TrxName());
		Env.setSOTrx(getCtx(), true);	//	Set SO context

		/** @todo duplicate invoice prevention */

		MOrder order = new MOrder (fromProject, true, MOrder.DocSubTypeSO_OnCredit);		
		order.setC_DocTypeTarget_ID(getDocTypeId());
		order.saveEx();

		//	***	Lines ***
		AtomicInteger count = new AtomicInteger(0);
		//	Service Project	
		if (MProject.PROJECTCATEGORY_ServiceChargeProject.equals(fromProject.getProjectCategory()))
		{
			/** @todo service project invoicing */
			throw new AdempiereException("Service Charge Projects are on the TODO List");
		}	//	Service Lines

		else	//	Order Lines
		{
			List<MProjectLine> fromProjectLines = fromProject.getLines();
			fromProjectLines.stream().forEach(projectLine -> {
				MOrderLine orderLine = new MOrderLine(order);
				orderLine.setLine(projectLine.getLine());
				orderLine.setDescription(projectLine.getDescription());
				orderLine.setM_Product_ID(projectLine.getM_Product_ID(), true);
				BigDecimal quantityToOrder = projectLine.getPlannedQty().subtract(projectLine.getInvoicedQty());
				MProduct product = MProduct.get(getCtx(), projectLine.getM_Product_ID());
				int uomId = product.getC_UOM_ID();
				if(projectLine.get_ValueAsInt("C_UOM_ID") > 0
						&& projectLine.get_Value("Qtyentered") != null) {
					uomId = projectLine.get_ValueAsInt("C_UOM_ID");
					if(uomId != product.getC_UOM_ID()) {
						BigDecimal quantityEntered = MUOMConversion.convertProductTo (getCtx(), projectLine.getM_Product_ID(), uomId, quantityToOrder);
						if (quantityEntered == null) {
							quantityEntered = quantityToOrder;
						}
						orderLine.setQty(quantityEntered);
						orderLine.setQtyOrdered(quantityToOrder);
					} else { 
						orderLine.setQty(quantityToOrder);
					}
				} else { 
					orderLine.setQty(quantityToOrder);
				}
				orderLine.setC_UOM_ID(uomId);
				orderLine.setPrice();
				if (projectLine.getPlannedPrice() != null && projectLine.getPlannedPrice().compareTo(Env.ZERO) != 0)
					orderLine.setPrice(projectLine.getPlannedPrice());
				orderLine.setDiscount();
				//	Project Reference
				orderLine.setC_Project_ID(projectLine.getC_Project_ID());
				//	Project Phase
				if(projectLine.getC_ProjectPhase_ID() > 0) {
					orderLine.setC_ProjectPhase_ID(projectLine.getC_ProjectPhase_ID());
				}
				//	Project Task
				if(projectLine.getC_ProjectTask_ID() > 0) {
					orderLine.setC_ProjectTask_ID(projectLine.getC_ProjectTask_ID());
				}
				orderLine.setTax();
				
				//BR [ 2111 ]
				orderLine.saveEx();
				count.getAndUpdate(no -> no + 1);
			});
			if (fromProjectLines.size() != count.get())
				log.log(Level.SEVERE, "Lines difference - ProjectLines=" + fromProjectLines.size() + " <> Saved=" + count.get());
		}	//	Order Lines

		return "@C_Order_ID@ " + order.getDocumentNo() + " (" + count + ")";
	}	//	doIt

	/**
	 * 	Get and validate Project
	 * 	@param ctx context
	 * 	@param projectId Project Id
	 * 	@return valid project
	 * 	@param trxName transaction
	 */
	static protected MProject getProject (Properties ctx, int projectId, String trxName)
	{
		MProject fromProject = new MProject (ctx, projectId, trxName);
		if (fromProject.getC_Project_ID() == 0)
			throw new AdempiereException("@C_Project_ID@ @NotFound@" + projectId);
		if (fromProject.getM_PriceList_ID() == 0)
			throw new AdempiereException("@M_PriceList_ID@ @NotFound@ @To@ @C_Project_ID@");
		if (fromProject.getM_Warehouse_ID() == 0)
			throw new AdempiereException("@M_Warehouse_ID@ @NotFound@ @To@ @C_Project_ID@");
		if (fromProject.getC_BPartner_ID() == 0)
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
		if (fromProject.getC_BPartner_Location_ID() == 0)
			throw new AdempiereException("@C_BPartner_Location_ID@ @NotFound@");
		return fromProject;
	}	//	getProject

}	//	ProjectGenOrder
