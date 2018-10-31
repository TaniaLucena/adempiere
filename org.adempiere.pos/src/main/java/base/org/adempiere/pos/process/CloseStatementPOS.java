/** ****************************************************************************
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
 * Copyright (C) 2003-2016 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): Victor Perez www.e-evolution.com                           *
 * ****************************************************************************/

package org.adempiere.pos.process;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MBankAccount;
import org.compiere.model.MBankStatement;
import org.compiere.model.MBankStatementLine;
import org.compiere.model.MOrg;
import org.compiere.model.MPayment;
import org.compiere.process.DocAction;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;


/**
 * Close Statement for POS
 * eEvolution author Victor Perez <victor.perez@e-evolution.com>, Created by e-Evolution on 04/15/16.
 */
public class CloseStatementPOS extends CloseStatementPOSAbstract {

    protected LinkedHashMap<Integer, MBankStatement> baskStatements = null;

    @Override
    protected void prepare() {
        super.prepare();
    }

    protected String doIt() throws Exception {
        if (getBankAccountId() <= 0)
            throw new AdempiereException("@C_BankAccount_ID@ @NotFound@");
        if (getTransactionDate() == null || getTransactionDateTo() == null )
            throw new AdempiereException("@DateTrx@ @NotFound@");

        if (getDifference().signum() != 0 && !isOverUnderPayment())
           return Msg.parseTranslation(getCtx() , "@C_BankStatement_ID@ @NotApproved@ @NotMatch@");
        else if (isOverUnderPayment())
        {
            if (getChargeId() <=0)
                throw new AdempiereException("@C_Charge_ID@ @NotFound@");
            // Generate Lost or Profit
            generateLostOrProfit();
        }
        //	Create Withdrawal
        createWithdrawal();
        // Close Bank Statement
        closeBankStatements();
        return "@Ok@";
    }

    private void closeBankStatements() {
        getBankStatements().entrySet().stream().forEach( entry -> {
            MBankStatement bankStatement = entry.getValue();
            bankStatement.processIt(DocAction.ACTION_Complete);
            bankStatement.saveEx();
        });
    }

    private void generateLostOrProfit() {
        MBankStatement bankStatement = (MBankStatement) getBankStatements().entrySet().iterator().next();
        MBankStatementLine bankStatementLine = new MBankStatementLine(bankStatement);
        bankStatementLine.setDateAcct(getTransactionDate());
        bankStatementLine.setStatementLineDate(getTransactionDateTo());
        bankStatementLine.setStmtAmt(getDifference());
        bankStatementLine.setC_Charge_ID(getChargeId());
        bankStatementLine.setChargeAmt(getDifference());
        bankStatementLine.saveEx();
    }
    
    /**
     * Create Withdrawal
     */
    private void createWithdrawal() {
    	BigDecimal payAmt = getPaidAmount();
    	BigDecimal cashLeaveAmt = getParameterAsBigDecimal("CashLeaveAmt");
    	String tenderType = getParameterAsString("TenderType");
    	if(payAmt == null 
    			|| cashLeaveAmt == null
    			|| cashLeaveAmt.doubleValue() == 0) {
    		return;
    	}
    	MBankAccount bankAccountFrom = MBankAccount.get(getCtx(), getBankAccountId());
    	MOrg org = MOrg.get(getCtx(), bankAccountFrom.getAD_Org_ID());
		int linkedBPartnerId = org.getLinkedC_BPartner_ID(get_TrxName());
		if (linkedBPartnerId == 0) {
			throw new AdempiereException("@LinkedC_BPartner_ID@ @of@ " + org.getName() + " @NotFound@");
		}
    	//	
    	if(Util.isEmpty(tenderType)) {
    		tenderType = MPayment.TENDERTYPE_DirectDeposit;
    	}
    	//	
    	BigDecimal differenceAmt = payAmt.subtract(cashLeaveAmt);
    	MBankAccount bankAccountTo = MBankAccount.get(getCtx(), getParameterAsInt("C_BankAccountTo_ID"));
    	MPayment paymentBankFrom = new MPayment(getCtx(), 0 ,  get_TrxName());
		paymentBankFrom.setC_BankAccount_ID(getBankAccountId());
		paymentBankFrom.setDateAcct(getTransactionDateTo());
		paymentBankFrom.setDateTrx(getTransactionDateTo());
		paymentBankFrom.setTenderType(tenderType);
		paymentBankFrom.setDescription(Msg.parseTranslation(getCtx(), "@Withdrawal@ @POS@"));
		paymentBankFrom.setC_BPartner_ID (linkedBPartnerId);
		paymentBankFrom.setC_Currency_ID(bankAccountFrom.getC_Currency_ID());
		paymentBankFrom.setPayAmt(differenceAmt);
		paymentBankFrom.setOverUnderAmt(Env.ZERO);
		paymentBankFrom.setC_DocType_ID(false);
		paymentBankFrom.setC_Charge_ID(getChargeId());
		paymentBankFrom.setC_POS_ID(getPOSTerminalId());
		paymentBankFrom.saveEx();
		//	
		MPayment paymentBankTo = new MPayment(getCtx(), 0 ,  get_TrxName());
		paymentBankTo.setC_BankAccount_ID(bankAccountTo.getC_BankAccount_ID());
		paymentBankTo.setDateAcct(getTransactionDateTo());
		paymentBankTo.setDateTrx(getTransactionDateTo());
		paymentBankTo.setTenderType(tenderType);
		paymentBankTo.setDescription(Msg.parseTranslation(getCtx(), "@Withdrawal@ @POS@"));
		paymentBankTo.setC_BPartner_ID (linkedBPartnerId);
		paymentBankTo.setC_Currency_ID(bankAccountTo.getC_Currency_ID());
		paymentBankTo.setPayAmt(differenceAmt);
		paymentBankTo.setOverUnderAmt(Env.ZERO);
		paymentBankTo.setC_DocType_ID(true);
		paymentBankTo.setC_Charge_ID(getChargeId());
		paymentBankTo.setC_POS_ID(getPOSTerminalId());
		paymentBankTo.saveEx();

		paymentBankFrom.setRelatedPayment_ID(paymentBankTo.getC_Payment_ID());
		paymentBankFrom.saveEx();
		paymentBankFrom.processIt(MPayment.DOCACTION_Complete);
		paymentBankFrom.saveEx();
		paymentBankTo.setRelatedPayment_ID(paymentBankFrom.getC_Payment_ID());
		paymentBankTo.saveEx();
		paymentBankTo.processIt(MPayment.DOCACTION_Complete);
		paymentBankTo.saveEx();
    }

    private LinkedHashMap<Integer, MBankStatement> getBankStatements()
    {
        if (baskStatements != null && baskStatements.size() > 0)
            return baskStatements;

        baskStatements = new LinkedHashMap<Integer, MBankStatement>();
        List<MPayment> payments = (List<MPayment>) getInstancesForSelection(get_TrxName());
        payments.stream().forEach( payment -> {
            Integer bankStatementLineId = getSelectionAsInt(payment.get_ID() , "BSL_C_BankStatementLine_ID");
            if (bankStatementLineId != null && bankStatementLineId > 0)
            {
                MBankStatementLine bankStatementLine = new MBankStatementLine(getCtx() , bankStatementLineId ,  get_TrxName());
                MBankStatement bankStatement = bankStatementLine.getParent();
                if (!baskStatements.containsKey(bankStatement.get_ID()))
                    baskStatements.put(bankStatement.get_ID() , bankStatement);
            }
        });
        return baskStatements;
    }
}
