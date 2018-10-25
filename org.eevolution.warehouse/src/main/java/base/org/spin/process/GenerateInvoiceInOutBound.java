/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.										*
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * or via info@adempiere.net or http://www.adempiere.net/license.html         *
 *****************************************************************************/

package org.spin.process;

import java.math.BigDecimal;
import java.util.Hashtable;
import java.util.List;

import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.Query;
import org.eevolution.model.MWMInOutBound;
import org.eevolution.model.MWMInOutBoundLine;

/** Generated Process for (Generate Invoice From Outbound Order)
 *  @author ADempiere (generated) 
 *  @version Release 3.9.0
 */
public class GenerateInvoiceInOutBound extends GenerateInvoiceInOutBoundAbstract {
	
	private Hashtable<Integer, MInvoice> invoices;
	private int created = 0;
	private StringBuffer generatedDocuments = new StringBuffer();
	
	@Override
	protected String doIt() throws Exception {
		invoices  = new Hashtable<Integer, MInvoice>();
		List<MWMInOutBoundLine> outBoundLines = null;
		//	Get from record
		if(getRecord_ID() > 0) {
			outBoundLines = new Query(getCtx(), MWMInOutBoundLine.Table_Name, MWMInOutBound.COLUMNNAME_WM_InOutBound_ID + "=?", get_TrxName())
				.setOrderBy(MWMInOutBoundLine.COLUMNNAME_C_Order_ID + ", " + MWMInOutBoundLine.COLUMNNAME_DD_Order_ID)
				.list();
		} else if(isSelection()) {
			// Overwrite table RV_WM_InOutBoundLine by WM_InOutBoundLine
			getProcessInfo().setTableSelectionId(MWMInOutBoundLine.Table_ID);
			outBoundLines = (List<MWMInOutBoundLine>) getInstancesForSelection(get_TrxName());
		}
		//	Create
		if(outBoundLines != null) {
			outBoundLines.stream().forEach(outBoundLine -> createInvoice(outBoundLine));
		}
		//	
		processingInvoices();
		//	
		return "@Created@ " + created + (generatedDocuments.length() > 0? " [" + generatedDocuments + "]": "");
	}
	
	/**
	 * Create Invoice from Outbound Line
	 * @param outboundLine
	 */
	private void createInvoice(MWMInOutBoundLine outboundLine) {
		if (outboundLine.getC_OrderLine_ID() > 0) {
			MOrderLine orderLine = outboundLine.getOrderLine();
			if (outboundLine.getPickedQty().subtract(orderLine.getQtyInvoiced()).signum() <= 0) {
				return;
			}

			BigDecimal qtyInvoiced = outboundLine.getPickedQty().subtract(orderLine.getQtyInvoiced());
			MInvoice invoice = getInvoice(orderLine, outboundLine.getParent());
			MInvoiceLine invoiceLine = new MInvoiceLine(outboundLine.getCtx(), 0 , outboundLine.get_TrxName());
			invoiceLine.setC_Invoice_ID(invoice.getC_Invoice_ID());
			invoiceLine.setM_Product_ID(outboundLine.getM_Product_ID());
			//	
			invoiceLine.setQtyEntered(qtyInvoiced);
			invoiceLine.setQtyInvoiced(qtyInvoiced);
			invoiceLine.setC_OrderLine_ID(orderLine.getC_OrderLine_ID());
			invoiceLine.saveEx();
			//	Set reference
			outboundLine.setC_Invoice_ID(invoice.getC_Invoice_ID());
			outboundLine.setC_InvoiceLine_ID(invoiceLine.getC_InvoiceLine_ID());
			outboundLine.saveEx();
		}
	}
	
	/**
	 * Create Invoice heder
	 * @param orderLine Sales Order Line
	 * @param outbound Outbound Order
	 * @return MInOut return the Shipment header
	 */
	private MInvoice getInvoice(MOrderLine orderLine, MWMInOutBound outbound) {
		MInvoice invoice = invoices.get(orderLine.getC_Order_ID());
		if(invoice != null)
			return invoice;

		MOrder order = orderLine.getParent();
		invoice = new MInvoice(order, 0, getDateInvoiced());
		if(getDocTypeTargetId() > 0) {
			invoice.setC_DocType_ID(getDocTypeTargetId());
		}
		invoice.setIsSOTrx(true);
		invoice.saveEx();

		invoices.put(order.getC_Order_ID(), invoice);
		return invoice;
	}
	
	/**
	 * Add Document Info for message to return
	 * @param documentInfo
	 */
	private void addToMessage(String documentInfo) {
		if(generatedDocuments.length() > 0) {
			generatedDocuments.append(", ");
		}
		//	
		generatedDocuments.append(documentInfo);
	}
	
	/**
	 * Process Invoices
	 */
	private void processingInvoices() {
		if(invoices == null) {
			return;
		}
		invoices.entrySet().stream().filter(entry -> entry != null).forEach(entry -> {
			MInvoice invoice = entry.getValue();
			invoice.setDocAction(getDocAction());
			if (!invoice.processIt(getDocAction())) {
				addLog("@ProcessFailed@ : " + invoice.getDocumentInfo());
				log.warning("@ProcessFailed@ :" + invoice.getDocumentInfo());
			}
			invoice.saveEx();
			created++;
			addToMessage(invoice.getDocumentNo());
		});
	}
}