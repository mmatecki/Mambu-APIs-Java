package demo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import com.mambu.accounting.shared.model.GLAccountingRule;
import com.mambu.accounts.shared.model.AccountHolderType;
import com.mambu.accounts.shared.model.AccountState;
import com.mambu.accounts.shared.model.InterestRateSource;
import com.mambu.accounts.shared.model.PredefinedFee;
import com.mambu.accounts.shared.model.PrincipalPaymentMethod;
import com.mambu.accounts.shared.model.TransactionChannel;
import com.mambu.accounts.shared.model.TransactionDetails;
import com.mambu.accountsecurity.shared.model.Guaranty;
import com.mambu.accountsecurity.shared.model.Guaranty.GuarantyType;
import com.mambu.accountsecurity.shared.model.InvestorFund;
import com.mambu.admin.shared.model.InterestProductSettings;
import com.mambu.admin.shared.model.PrincipalPaymentProductSettings;
import com.mambu.api.server.handler.loan.model.JSONLoanAccountResponse;
import com.mambu.api.server.handler.loan.model.JSONRestructureEntity;
import com.mambu.api.server.handler.loan.model.RestructureDetails;
import com.mambu.apisdk.MambuAPIFactory;
import com.mambu.apisdk.exception.MambuApiException;
import com.mambu.apisdk.services.DocumentsService;
import com.mambu.apisdk.services.LoansService;
import com.mambu.apisdk.services.SavingsService;
import com.mambu.apisdk.util.APIData;
import com.mambu.apisdk.util.APIData.CLOSER_TYPE;
import com.mambu.apisdk.util.DateUtils;
import com.mambu.apisdk.util.MambuEntityType;
import com.mambu.clients.shared.model.Client;
import com.mambu.clients.shared.model.Group;
import com.mambu.core.shared.model.CustomFieldType;
import com.mambu.core.shared.model.CustomFieldValue;
import com.mambu.core.shared.model.LoanPenaltyCalculationMethod;
import com.mambu.core.shared.model.Money;
import com.mambu.core.shared.model.RepaymentAllocationElement;
import com.mambu.core.shared.model.User;
import com.mambu.docs.shared.model.Document;
import com.mambu.loans.shared.model.AmortizationMethod;
import com.mambu.loans.shared.model.CustomPredefinedFee;
import com.mambu.loans.shared.model.DisbursementDetails;
import com.mambu.loans.shared.model.GracePeriodType;
import com.mambu.loans.shared.model.LoanAccount;
import com.mambu.loans.shared.model.LoanAccount.RepaymentPeriodUnit;
import com.mambu.loans.shared.model.LoanProduct;
import com.mambu.loans.shared.model.LoanProductType;
import com.mambu.loans.shared.model.LoanTranche;
import com.mambu.loans.shared.model.LoanTransaction;
import com.mambu.loans.shared.model.LoanTransactionType;
import com.mambu.loans.shared.model.PrincipalPaymentAccountSettings;
import com.mambu.loans.shared.model.Repayment;
import com.mambu.loans.shared.model.RepaymentScheduleMethod;
import com.mambu.loans.shared.model.ScheduleDueDatesMethod;
import com.mambu.savings.shared.model.SavingsAccount;

import demo.DemoUtil.FeeCategory;

/**
 * Test class to show example usage of the api calls
 * 
 * @author edanilkis
 * 
 */
public class DemoTestLoanService {

	private static String NEW_LOAN_ACCOUNT_ID; // will be assigned after creation in testCreateJsonAccount()

	private static Client demoClient;
	private static Group demoGroup;
	private static User demoUser;
	private static LoanProduct demoProduct;
	private static LoanAccount demoLoanAccount;

	private static LoanAccount newAccount;

	private static String methodName = null; // print method name on exception

	public static void main(String[] args) {

		DemoUtil.setUp();

		try {
			// Get demo entities needed for testing
			// Use specific product ID or null to get random product. If set to "ALL" then test for all product types
			final String testProductId = DemoUtil.demoLaonProductId;
			final String testAccountId = null; // use specific test ID or null to get random loan account
			demoClient = DemoUtil.getDemoClient(null);
			demoGroup = DemoUtil.getDemoGroup(null);
			demoUser = DemoUtil.getDemoUser();

			LoanProductType productTypes[];
			boolean productTypesTesting;
			// If demoLaonProductId configuration set to ALL then test all available product types
			if (testProductId != null && testProductId.equals(DemoUtil.allProductTypes)) {
				productTypesTesting = true;
				// Run tests for all products types, selecting random active product ID of each type once
				productTypes = LoanProductType.values();
			} else {
				// Use demoLaonProductId configuration: a specific product ID (if not null) or a random product (if
				// null)
				productTypesTesting = false;
				LoanProduct testProduct = DemoUtil.getDemoLoanProduct(testProductId);
				// Set test product types to the demoLaonProductId's type only
				productTypes = new LoanProductType[] { testProduct.getLoanProductType() };
			}

			// Run tests for all required product types
			for (LoanProductType productType : productTypes) {
				System.out.println("\n*** Product Type=" + productType + " ***");

				// Get random product of a specific type or a product for a specific product id
				demoProduct = (productTypesTesting) ? DemoUtil.getDemoLoanProduct(productType) : DemoUtil
						.getDemoLoanProduct(testProductId);

				if (demoProduct == null) {
					continue;
				}
				System.out.println("Product Id=" + demoProduct.getId() + " Name=" + demoProduct.getName() + " ***");

				demoLoanAccount = DemoUtil.getDemoLoanAccount(testAccountId);
				System.out.println("Using Demo Loan Account=" + demoLoanAccount.getId() + "\tName="
						+ demoLoanAccount.getName());

				try {
					// Create account to test patch, approve, undo approve, reject, close
					testCreateJsonAccount();
					testPatchLoanAccountTerms(); // Available since 3.9.3
					testApproveLoanAccount();
					testUndoApproveLoanAccount();
					testUpdateLoanAccount();

					// Test Reject transactions first
					testCloseLoanAccount(CLOSER_TYPE.REJECT); // Available since 3.3
					testDeleteLoanAccount();

					// Create new account to test approve, undo approve, disburse, undo disburse, updating tranches and
					// funds and then locking and unlocking. Test Repay and Close account
					testCreateJsonAccount();
					testRequestApprovalLoanAccount(); // Available since 3.13
					testApproveLoanAccount();
					testUpdatingAccountTranches(); // Available since 3.12.3
					testUpdatingAccountFunds(); // Available since 3.13

					// Test Disburse and Undo disburse
					testDisburseLoanAccount();
					testUndoDisburseLoanAccount(); // Available since 3.9
					testDisburseLoanAccount();
					testApplyFeeToLoanAccount();
					testGetLoanAccountTransactions();

					testLockLoanAccount(); // Available since 3.6
					testUnlockLoanAccount(); // Available since 3.6

					// Repay Loan account to test Close account
					testRepayLoanAccount(true);
					testCloseLoanAccount(CLOSER_TYPE.CLOSE);

					// Test Other methods. Create new account
					testCreateJsonAccount();
					testUpdatingAccountTranches(); // Available since 3.12.3
					testUpdatingAccountFunds(); // Available since 3.13
					testUpdateLoanAccountGuarantees(); // Available since 4.0
					testRequestApprovalLoanAccount(); // Available since 3.13
					testApproveLoanAccount();
					testDisburseLoanAccount();
					testApplyInterestToLoanAccount(); // Available since 3.1
					testApplyFeeToLoanAccount();

					// Get product Schedule
					testGetLoanProductSchedule(); // Available since 3.9

					// Get Loan Details
					testGetLoanAccount();
					testGetLoanAccountDetails();
					testGetLoanWithSettlemntAccounts(); // Available since 4.0.

					// Test Update and Delete Custom fields
					testUpdateDeleteCustomFields(); // Available since 3.8

					// Get Loans
					testGetLoanAccountsByBranchCentreOfficerState();
					testGetLoanAccountsForClient();
					testGetLoanAccountsForGroup();

					// Get transactions
					List<LoanTransaction> transactions = testGetLoanAccountTransactions();
					testReverseLoanAccountTransactions(transactions); // Available since Mambu 3.13 for PENALTY_APPLIED

					// Repay and Write off
					testRepayLoanAccount(false); // Make partial repayment
					testWriteOffLoanAccount(); // Available since 3.14

					// Test refinancing
					// Create new test account to test these APIs
					testCreateJsonAccount();
					testRequestApprovalLoanAccount(); // Available since 3.13
					testApproveLoanAccount();
					testDisburseLoanAccount();
					// Reschedule and Refinance Loan account
					testRescheduleAndRefinanceLoanAccount(); // Available since 4.1

					// Products
					testGetLoanProducts();
					testGetLoanProductById();

					// Documents
					testGetDocuments(); // Available since Mambu 3.6

				} catch (MambuApiException e) {
					DemoUtil.logException(methodName, e);
					System.out.println("Product Type=" + demoProduct.getLoanProductType() + "\tID="
							+ demoProduct.getId() + "\tName=" + demoProduct.getName());
				}
			}

		} catch (MambuApiException e) {
			System.out.println("Exception caught in Demo Test Loan Service");
			System.out.println("Error code=" + e.getErrorCode());
			System.out.println(" Cause=" + e.getCause() + ".  Message=" + e.getMessage());
		}

	}

	public static void testGetLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();

		String accountId = NEW_LOAN_ACCOUNT_ID; // LOAN_ACCOUNT_ID NEW_LOAN_ACCOUNT_ID

		System.out.println("Got loan account  by ID: " + loanService.getLoanAccount(accountId).getName());

	}

	public static void testGetLoanAccountDetails() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanAccountDetails");

		LoansService loanService = MambuAPIFactory.getLoanService();
		String accountId = NEW_LOAN_ACCOUNT_ID;

		LoanAccount loanDeatils = loanService.getLoanAccountDetails(accountId);
		System.out.println("Got loan account by ID with details: " + loanDeatils.getName() + "\tId="
				+ loanDeatils.getId());

		// Log Loan's Disbursement Details. Available since 4.0. See MBU-11223
		DisbursementDetails disbDetails = loanDeatils.getDisbursementDetails();
		logDisbursementDetails(disbDetails);

		// If account has Securities, log their custom info to test MBU-7684
		// See MBU-7684 As a Developer, I need to work with guarantees with custom fields
		List<Guaranty> guarantees = loanDeatils.getGuarantees();
		if (guarantees == null) {
			System.out.println("Account has no guarantees defined");
			return;
		}

		System.out.println("Logging Guarantees:");
		for (Guaranty guaranty : guarantees) {
			System.out.println("Guarantor type=" + guaranty.getType() + "\tAssetName=" + guaranty.getAssetName()
					+ "\tGurantor Key=" + guaranty.getGuarantorKey() + "\tSavinsg Key="
					+ guaranty.getSavingsAccountKey() + "\tAmount=" + guaranty.getAmount());

			List<CustomFieldValue> guarantyCustomValues = guaranty.getCustomFieldValues();
			DemoUtil.logCustomFieldValues(guarantyCustomValues, "Guarantor", guaranty.getEncodedKey());
		}
		// Log Investor Funds. Available since Mambu 3.13. See MBU-9887
		List<InvestorFund> funds = loanDeatils.getFunds();
		if (funds == null) {
			System.out.println("Account has no investor funds defined");
			return;
		}
		System.out.println("Logging Investor Funds:");
		for (InvestorFund fund : funds) {
			System.out.println("Guarantor type=" + fund.getType() + "\tGurantor Key=" + fund.getGuarantorKey()
					+ "\tSavinsg Key=" + fund.getSavingsAccountKey() + "\tAmount=" + fund.getAmount());

			List<CustomFieldValue> fundCustomValues = fund.getCustomFieldValues();
			DemoUtil.logCustomFieldValues(fundCustomValues, "Fund", fund.getEncodedKey());
		}
	}

	// Create Loan Account
	public static void testCreateJsonAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testCreateJsonAccount");

		LoansService loanService = MambuAPIFactory.getLoanService();

		LoanAccount account = makeLoanAccountForDemoProduct();

		// Set Custom fields to null in the account (they are sent in customInformation field)
		account.setCustomFieldValues(null);

		// Use helper to make test custom fields valid for the account's product
		List<CustomFieldValue> clientCustomInformation = DemoUtil.makeForEntityCustomFieldValues(
				CustomFieldType.LOAN_ACCOUNT_INFO, demoProduct.getEncodedKey());
		account.setCustomFieldValues(clientCustomInformation);

		// Create Account in Mambu
		newAccount = loanService.createLoanAccount(account);
		NEW_LOAN_ACCOUNT_ID = newAccount.getId();

		System.out.println("Loan Account created OK, ID=" + newAccount.getId() + " Name= " + newAccount.getLoanName()
				+ " Account Holder Key=" + newAccount.getAccountHolderKey());

		// Log Disbursement Details
		logDisbursementDetails(newAccount.getDisbursementDetails());

		// Check returned custom fields after create
		List<CustomFieldValue> customFieldValues = newAccount.getCustomFieldValues();
		// Log Custom Field Values
		DemoUtil.logCustomFieldValues(customFieldValues, newAccount.getLoanName(), newAccount.getId());

	}

	// Update Loan account
	public static void testUpdateLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testUpdateLoanAccount");

		LoansService loanService = MambuAPIFactory.getLoanService();

		// Use the newly created account and update some custom fields
		LoanAccount updatedAccount = newAccount;
		List<CustomFieldValue> customFields = newAccount.getCustomFieldValues();
		List<CustomFieldValue> updatedFields = new ArrayList<CustomFieldValue>();

		if (customFields != null) {
			for (CustomFieldValue value : customFields) {
				value = DemoUtil.makeNewCustomFieldValue(value);
				updatedFields.add(value);
			}
		} else {
			System.out.println("WARNING: no custom fields to update for account " + updatedAccount.getId());
		}

		// Update account in Mambu
		LoanAccount updatedAccountResult = loanService.updateLoanAccount(updatedAccount);

		System.out.println("Loan Update OK, ID=" + updatedAccountResult.getId() + "\tAccount Name="
				+ updatedAccountResult.getName());

		// Get returned custom fields
		List<CustomFieldValue> updatedCustomFields = updatedAccountResult.getCustomFieldValues();

		if (updatedCustomFields != null) {
			System.out.println("Custom Fields for Loan Account\n");
			for (CustomFieldValue value : updatedCustomFields) {
				System.out.println("CustomFieldKey=" + value.getCustomFieldKey() + "\tValue=" + value.getValue()
						+ "\tName=" + value.getCustomField().getName());
			}
		}

	}

	// Test Patch Loan account terms API.
	public static void testPatchLoanAccountTerms() throws MambuApiException {
		System.out.println(methodName = "\nIn testPatchLoanAccountTerms");

		LoansService loanService = MambuAPIFactory.getLoanService();

		// Use the newly created account and update some terms fields
		LoanAccount theAccount = newAccount;
		// Create new account with only the terms to be patched
		LoanAccount account = new LoanAccount();
		account.setId(theAccount.getId()); // set the ID, the encoded key cannot be set with 3.14 model

		// Set fields to be updated. Update some account terms
		account.setLoanAmount(theAccount.getLoanAmount()); // loanAmount
		if (demoProduct.getInterestRateSettings().getInterestRateSource() == InterestRateSource.FIXED_INTEREST_RATE) {
			account.setInterestRate(theAccount.getInterestRate()); // interestRate
		} else {
			account.setInterestSpread(theAccount.getInterestSpread());
		}
		// Leave some other updatable terms unchanged
		account.setRepaymentInstallments(theAccount.getRepaymentInstallments()); // repaymentInstallments
		account.setRepaymentPeriodCount(theAccount.getRepaymentPeriodCount()); // repaymentPeriodCount
		account.setRepaymentPeriodUnit(theAccount.getRepaymentPeriodUnit()); // repaymentPeriodUnit
		account.setDisbursementDetails(theAccount.getDisbursementDetails()); // to update the first repayment date and
																				// the expected disbursement

		// Set deprecated fields to null not to be used when patching account
		account.setExpectedDisbursementDate(null);
		account.setFirstRepaymentDate(null);

		account.setGracePeriod(theAccount.getGracePeriod()); // gracePeriod
		account.setPrincipalRepaymentInterval(theAccount.getPrincipalRepaymentInterval()); // principalRepaymentInterval
		account.setPenaltyRate(theAccount.getPenaltyRate()); // penaltyRate
		account.setPeriodicPayment(theAccount.getPeriodicPayment()); // periodicPayment

		// Test Principal Payment for REVOLVING CREDIT. See MBU-12143

		account.setPrincipalPaymentSettings(theAccount.getPrincipalPaymentSettings());
		// Patch loan account terms
		boolean result = loanService.patchLoanAccount(account);
		System.out.println("Loan Terms Update for account. Status=" + result);

	}

	// Test Updating Loan Account Tranches API: modify, add, delete
	public static void testUpdatingAccountTranches() throws MambuApiException {
		System.out.println(methodName = "\nIn testUpdatingAccountTranches");

		// Use demo loan account and update tranche details
		LoanAccount theAccount = newAccount;
		String accountId = theAccount.getId();

		// Get Loan Account Tranches
		List<LoanTranche> allTranches = theAccount.getTranches();
		if (allTranches == null || allTranches.size() == 0) {
			System.out.println("WARNING: cannot test update tranches: loan account " + theAccount.getId()
					+ " doesn't have tranches");
			return;
		}
		// See if we have any non-disbursed tranches
		List<LoanTranche> nonDisbursedTranches = theAccount.getNonDisbursedTranches();
		if (nonDisbursedTranches == null || nonDisbursedTranches.size() == 0) {
			System.out.println("WARNING: cannot test update tranches: loan account " + theAccount.getId()
					+ " doesn't have any non-disbursed tranches");
			return;
		}
		List<LoanTranche> disbursedTranches = theAccount.getDisbursedTranches();
		boolean hasDisbursedTranches = disbursedTranches != null && disbursedTranches.size() > 0;
		// Test updating tranches first
		Date trancheDate = DemoUtil.getAsMidnightUTC();
		long fiveDays = 5 * 24 * 60 * 60 * 1000L; // 5 days in msecs
		int i = 0;
		Date firstRepaymentDate = theAccount.getFirstRepaymentDateFromDisbursementDetails();
		for (LoanTranche tranche : nonDisbursedTranches) {
			trancheDate = new Date(trancheDate.getTime() + i * fiveDays); // make tranche dates to be some days apart
			// The first tranche cannot have expected disbursement date after the first repayment date
			if (!hasDisbursedTranches && firstRepaymentDate != null && i == 0) {
				if (trancheDate.after(firstRepaymentDate)) {
					trancheDate = firstRepaymentDate;
				}
			}
			tranche.setExpectedDisbursementDate(trancheDate);
			i++;

		}
		System.out.println("\nUpdating existent tranches");
		LoansService loanService = MambuAPIFactory.getLoanService();
		LoanAccount result = loanService.updateLoanAccountTranches(accountId, nonDisbursedTranches);

		System.out.println("Loan Tranches updated for account " + accountId + " Total New Tranches="
				+ result.getNonDisbursedTranches().size());

		// Test deleting and then adding tranches now. Setting tranche's encoded key to null should result in all
		// existent tranches being deleted and the new ones (with the same data) created
		for (LoanTranche tranche : nonDisbursedTranches) {
			// Set all encoded keys to null. This would treat these tranches as new ones
			// The original versions will be deleted
			tranche.setEncodedKey(null);
		}
		System.out.println("\nDeleting and re-creating the same tranches");
		LoanAccount result2 = loanService.updateLoanAccountTranches(accountId, nonDisbursedTranches);
		System.out.println("Loan Tranches deleted and added for account " + accountId + " Total New Tranches="
				+ result2.getNonDisbursedTranches().size());
	}

	// Test Updating Loan Account Funds API: modify, add, delete
	public static void testUpdatingAccountFunds() throws MambuApiException {
		System.out.println(methodName = "\nIn testUpdatingAccountFunds");

		// Use demo loan account and update investor funds details
		LoanAccount theAccount = newAccount;
		String accountId = theAccount.getId();

		// Get Loan Account Funds
		List<InvestorFund> funds = theAccount.getFunds();
		if (funds == null || funds.size() == 0) {
			System.out.println("WARNING: cannot test update funds: loan account " + theAccount.getId()
					+ " doesn't have funds");
			return;
		}

		// Test updating existent funds first
		for (InvestorFund fund : funds) {
			fund.setAmount(fund.getAmount().add(new BigDecimal(50.0)));

		}
		System.out.println("\nUpdating existent funds");
		LoansService loanService = MambuAPIFactory.getLoanService();
		LoanAccount result = loanService.updateLoanAccountFunds(accountId, funds);

		System.out.println("Loan Funds updated for account " + accountId + " Total New Funds="
				+ result.getFunds().size());

		// Test deleting and then adding funds now. Setting fund's encoded keys to null should result in these
		// existent funds being deleted and the new ones (with the same data) created
		List<InvestorFund> resultFunds = result.getFunds();
		List<InvestorFund> updatedFunds = new ArrayList<>();
		for (InvestorFund fund : resultFunds) {
			// Create new funds as copies of the original (but without the encoded key). This would treat these funds as
			// new ones. The original versions will be deleted
			InvestorFund newFund = new InvestorFund();
			// Make a copy
			newFund.setAmount(fund.getAmount());
			newFund.setGuarantorKey(fund.getGuarantorKey());
			newFund.setSavingsAccountKey(fund.getSavingsAccountKey());
			newFund.setCustomFieldValues(fund.getCustomFieldValues());
			updatedFunds.add(newFund);

		}
		System.out.println("\nDeleting and re-creating the same funds");
		LoanAccount result2 = loanService.updateLoanAccountFunds(accountId, updatedFunds);
		System.out.println("Loan Funds deleted and added for account " + accountId + " Total New Funds="
				+ result2.getFunds().size());
	}

	// Test getting Savings Settlement Accounts for a loan account.
	public static void testGetLoanWithSettlemntAccounts() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanWithSettlemntAccounts");

		// Get Settlement accounts
		String accountId = NEW_LOAN_ACCOUNT_ID;
		LoansService loanService = MambuAPIFactory.getLoanService();
		System.out.println("Getting Settlement Account for Loan " + accountId);
		JSONLoanAccountResponse loanAccountResponse = loanService.getLoanAccountWithSettlementAccounts(accountId);
		// Log Settlement Accounts details
		List<SavingsAccount> settlementAccounts = loanAccountResponse.getSettlementAccounts();
		int totalAccounts = settlementAccounts == null ? 0 : settlementAccounts.size();
		System.out.println("Total Settlement Accounts-" + totalAccounts + " for Account ID=" + accountId);
		if (totalAccounts == 0) {
			return;
		}
		// Log some details for individual accounts
		for (SavingsAccount savings : settlementAccounts) {
			System.out.println("\tSavings ID=" + savings.getId() + "\tName=" + savings.getName() + "\tHolderKey="
					+ savings.getAccountHolderKey());
		}

	}

	// Test Updating Loan Account Guarantees API: modify, add, delete
	public static void testUpdateLoanAccountGuarantees() throws MambuApiException {
		System.out.println(methodName = "\nIn testUpdateLoanAccountGuarantees");

		// Use demo loan account and update investor guarantees
		LoanAccount theAccount = newAccount;
		String accountId = theAccount.getId();

		// Get Loan Account Guarantees
		List<Guaranty> guaraantees = theAccount.getGuarantees();
		if (guaraantees == null || guaraantees.size() == 0) {
			System.out.println("WARNING: cannot test update Guarantees: loan account " + theAccount.getId()
					+ " doesn't have Guarantees");
			return;
		}

		// Test updating existent guarantees first
		for (Guaranty guaranty : guaraantees) {
			guaranty.setAmount(guaranty.getAmount().add(new BigDecimal(50.0)));

		}
		System.out.println("\nUpdating existent guarantees");
		LoansService loanService = MambuAPIFactory.getLoanService();
		LoanAccount result = loanService.updateLoanAccountGuarantees(accountId, guaraantees);

		System.out.println("Loan Guarantees updated for account " + accountId + " Total New Guarantees="
				+ result.getGuarantees().size());

		// Test deleting and then adding guarantees now. Setting guaranty's encoded key to null should result in these
		// existent guarantees being deleted and the new ones (with the same data) created
		List<Guaranty> resultGuarantees = result.getGuarantees();
		List<Guaranty> updatedGuarantees = new ArrayList<>();
		for (Guaranty guaranty : resultGuarantees) {
			// Create new guarantees as copies of the original (but without the encoded key). This would treat these
			// guarantees as new ones. The original versions will be deleted
			Guaranty newGuaranty = new Guaranty();
			// Make a copy. This new Guaranty won't have the encoded key, so it will be created
			newGuaranty.setType(guaranty.getType());
			newGuaranty.setGuarantorType(guaranty.getGuarantorType());
			newGuaranty.setAssetName(guaranty.getAssetName());
			newGuaranty.setAmount(guaranty.getAmount());
			newGuaranty.setGuarantorKey(guaranty.getGuarantorKey());
			newGuaranty.setSavingsAccountKey(guaranty.getSavingsAccountKey());
			newGuaranty.setCustomFieldValues(guaranty.getCustomFieldValues());
			updatedGuarantees.add(newGuaranty);

		}
		System.out.println("\nDeleting and re-creating the same guarantees");
		LoanAccount result2 = loanService.updateLoanAccountGuarantees(accountId, updatedGuarantees);
		System.out.println("Loan Guarantees deleted and added for account " + accountId + " Total New Guarantees="
				+ result2.getGuarantees().size());
	}

	// / Transactions testing
	public static void testDisburseLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testDisburseLoanAccount");

		LoansService loanService = MambuAPIFactory.getLoanService();
		if (newAccount == null) {
			System.out.println("\nThere is no account to disburse");
		}
		;
		LoanAccount account = newAccount;

		String accountId = account.getId();
		Date disbursementDate = DemoUtil.getAsMidnightUTC();
		// Make First Repayment Date
		Date firstRepaymentDate = makeFirstRepaymentDate(account, demoProduct, true);

		// Since 3.14, the disbursement amount must be specified only for Revolving Credit products and can be null for
		// others. See MBU-10547 and MBU-11058
		Money amount = null;
		LoanProductType productType = demoProduct.getLoanProductType();
		switch (productType) {
		case DYNAMIC_TERM_LOAN:
		case FIXED_TERM_LOAN:
		case PAYMENT_PLAN:
			// Amount can be null
			amount = null;
			break;
		case TRANCHED_LOAN:
			// For loan account with tranches in Mambu 3.13. See MBU-10045
			// a) Amount must be null.
			// b) Only the first tranche can have firstRepaymentDate
			// c) the backdate is also not needed for disbursing with tranches
			amount = null;
			disbursementDate = null;
			// Check if we have ny tranches to disburse
			List<LoanTranche> nonDisbursedTranches = account.getNonDisbursedTranches();
			if (nonDisbursedTranches == null || nonDisbursedTranches.size() == 0) {
				System.out.println("WARNING: Cannot test disburse: Loan  " + account.getId()
						+ " has non disbursed tranches");
				return;
			}
			// Check if we the disburse time is not in a future
			Date expectedTrancheDisbDate = nonDisbursedTranches.get(0).getExpectedDisbursementDate();
			Date now = new Date();
			if (expectedTrancheDisbDate.after(now)) {
				System.out.println("WARNING: cannot disburse tranche. Its ExpectedDisbursementDate="
						+ expectedTrancheDisbDate);
				return;
			}
			// If not the first tranche - set the firstRepaymentDate to null
			if (account.getDisbursedTranches() != null && account.getDisbursedTranches().size() > 0) {
				firstRepaymentDate = null;
			} else {
				// First tranche. Can optionally set the first repayment date
				if (firstRepaymentDate != null && firstRepaymentDate.before(expectedTrancheDisbDate)) {
					long fiveDays = 5 * 24 * 60 * 60 * 1000L;
					firstRepaymentDate = new Date(firstRepaymentDate.getTime() + fiveDays);
				}
			}
			break;
		case REVOLVING_CREDIT:
			// Amount is mandatory for Revolving Credit loans. See MBU-10547
			amount = account.getLoanAmount();
			// First repayment date should be specified only for the first disbursement (when transitioning from
			// Approved to Active state)
			if (account.getAccountState() != AccountState.APPROVED) {
				firstRepaymentDate = null;
			}
			break;
		}
		// Create params for API
		String disbursementDateParam = DateUtils.format(disbursementDate);
		String firstRepaymentDateParam = DateUtils.format(firstRepaymentDate);
		System.out.println("Disbursement=" + disbursementDateParam + "\tFirstRepaymentDate=" + firstRepaymentDateParam);
		String notes = "Disbursed loan for testing";

		// Set disbursement dates in the DisbursementDetails
		DisbursementDetails disbDetails = newAccount.getDisbursementDetails();
		if (disbDetails == null) {
			disbDetails = new DisbursementDetails();
		}
		disbDetails.setFirstRepaymentDate(firstRepaymentDate);
		disbDetails.setExpectedDisbursementDate(disbursementDate);

		// Set up disbursement Fees as per Mambu expectations. See MBU-8811
		List<CustomPredefinedFee> disbursementFees = DemoUtil.makeDemoPredefinedFees(demoProduct, new HashSet<>(
				Collections.singletonList(FeeCategory.DISBURSEMENT)));
		disbDetails.setFees(disbursementFees);

		// Test setting Transaction Channel Custom fields
		TransactionDetails accountTransactionDetails = disbDetails.getTransactionDetails();
		if (accountTransactionDetails == null) {
			accountTransactionDetails = new TransactionDetails();
		}
		// Get channel for custom fields
		TransactionChannel channel = accountTransactionDetails.getTransactionChannel();
		if (channel == null) {
			channel = DemoUtil.getDemoTransactionChannel();
		}
		// Create new TransactionDetails: in 4.1 we need only the channel and they must NOT have deprecated channel
		// fields too (they are returned by Mambu in Create response)> Otherwise Mambu returns:
		// "returnCode":918,"returnStatus":"DUPLICATE_CUSTOM_FIELD_VALUES",
		TransactionDetails transactionDetails = new TransactionDetails();
		transactionDetails.setTransactionChannel(channel);
		disbDetails.setTransactionDetails(transactionDetails);

		// Make transaction custom fields
		String channelKey = channel.getEncodedKey();
		// Use CustomFieldValue specified on Create, if any

		List<CustomFieldValue> transactionFields = disbDetails.getCustomFieldValues();
		if (transactionFields == null || transactionFields.size() == 0) {
			// Make new ones for this channel
			System.out.println("Creating new transaction fields for channel=" + channel.getId());
			transactionFields = DemoUtil.makeForEntityCustomFieldValues(CustomFieldType.TRANSACTION_CHANNEL_INFO,
					channelKey, false);
		}

		try {
			// Send API request to Mambu to test JSON Disburse API
			LoanTransaction transaction = loanService.disburseLoanAccount(accountId, amount, disbDetails,
					transactionFields, notes);
			System.out.println("Disbursed OK: Transaction Id=" + transaction.getTransactionId() + " amount="
					+ transaction.getAmount());

			// Since 4.1 Channel fields are returned as custom fields
			List<CustomFieldValue> transactionCustomInformation = transaction.getCustomFieldValues();
			DemoUtil.logCustomFieldValues(transactionCustomInformation, "LoanTransaction", accountId);
		} catch (MambuApiException e) {

			DemoUtil.logException(methodName, e);
		}
	}

	// Test undo disbursement transaction
	public static void testUndoDisburseLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testUndoDisburseLoanAccount");
		try {
			LoansService loanService = MambuAPIFactory.getLoanService();
			String accountId = NEW_LOAN_ACCOUNT_ID;
			String notes = "Undo disbursement via Demo API";
			LoanTransaction transaction = loanService.undoDisburseLoanAccount(accountId, notes);
			System.out.println("\nOK Undo Loan Disbursement for account=" + accountId + "\tTransaction Id="
					+ transaction.getTransactionId());
		} catch (MambuApiException e) {

			DemoUtil.logException(methodName, e);
		}

	}

	// Test writing off loan account
	public static void testWriteOffLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testWriteOffLoanAccount");

		LoansService loanService = MambuAPIFactory.getLoanService();
		String accountId = NEW_LOAN_ACCOUNT_ID;
		String notes = "Write off account via Demo API";
		LoanTransaction transaction = loanService.writeOffLoanAccount(accountId, notes);
		System.out.println("\nOK Write Off for account=" + accountId + "\tTransaction Id="
				+ transaction.getTransactionId());

	}

	public static List<LoanTransaction> testGetLoanAccountTransactions() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanAccountTransactions");
		LoansService loanService = MambuAPIFactory.getLoanService();
		String offest = "0";
		String limit = "8";

		final String accountId = NEW_LOAN_ACCOUNT_ID;
		List<LoanTransaction> loanTransactions = loanService.getLoanAccountTransactions(accountId, offest, limit);

		System.out.println("Got loan accounts transactions, total=" + loanTransactions.size()
				+ " in a range for the Loan with the " + accountId + " id:" + " Range=" + offest + "  " + limit);

		return loanTransactions;
	}

	// Test Reversing loan transactions. Available since 3.13 for PENALTY_APPLIED transaction. See MBU-9998
	public static void testReverseLoanAccountTransactions(List<LoanTransaction> transactions) throws MambuApiException {
		System.out.println(methodName = "\nIn testReverseLoanAccountTransactions");

		if (transactions == null || transactions.size() == 0) {
			System.out.println("WARNING: no transactions available to test transactions reversal");
		}
		LoansService loanService = MambuAPIFactory.getLoanService();

		// Try reversing any of the supported types. Some calls may throw validation exceptions (if not allowed for
		// reversal)
		boolean reversalTested = false;
		for (LoanTransaction transaction : transactions) {
			LoanTransactionType originalTransactionType = transaction.getType();
			// as of Mambu 3.13 only PENALTY_APPLIED transaction can be reversed
			switch (originalTransactionType) {
			case PENALTY_APPLIED:
				reversalTested = true;
				// Try reversing supported transaction type
				// Catch exceptions: For example, if there were later transactions logged after this one then Mambu
				// would return an exception
				try {
					String reversalNotes = "Reversed " + originalTransactionType + " by Demo API";
					String originalTransactionId = String.valueOf(transaction.getTransactionId());
					LoanTransaction reversed = loanService.reverseLoanTransaction(demoLoanAccount.getId(),
							originalTransactionType, originalTransactionId, reversalNotes);

					System.out.println("Reversed Transaction " + transaction.getType() + "\tReversed Amount="
							+ reversed.getAmount().toString() + "\tBalance =" + reversed.getBalance().toString()
							+ "Transaction Type=" + reversed.getType() + "\tAccount key="
							+ reversed.getParentAccountKey());
				} catch (MambuApiException e) {
					DemoUtil.logException(methodName, e);
					continue;
				}
				break;
			default:
				break;
			}
		}
		if (!reversalTested) {
			System.out
					.println("WARNING: no transaction types supporting reversal are available to test transaction reversal");
		}
	}

	// Test loan repayments. Set fullRepayment to true to test fully repaying loan
	public static void testRepayLoanAccount(boolean fullRepayment) throws MambuApiException {
		System.out.println(methodName = "\nIn testRepayLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();
		String accountId = newAccount.getId();
		// Get the latest account and its balance
		LoanAccount account = loanService.getLoanAccountDetails(accountId);
		Money repaymentAmount = fullRepayment ? account.getTotalBalanceOutstanding() : account
				.getDueAmount(RepaymentAllocationElement.PRINCIPAL);
		System.out.println("Repayment Amount=" + repaymentAmount);
		if (repaymentAmount == null || repaymentAmount.isNegativeOrZero()) {
			repaymentAmount = new Money(320);
		}

		Date date = null; // "2012-11-23";
		String notes = "repayment notes from API";

		// Make demo transactionDetails with the valid channel fields
		TransactionDetails transactionDetails = DemoUtil.makeDemoTransactionDetails();
		String channelKey = transactionDetails != null ? transactionDetails.getTransactionChannel().getEncodedKey()
				: null;
		List<CustomFieldValue> transactionCustomFields = DemoUtil.makeForEntityCustomFieldValues(
				CustomFieldType.TRANSACTION_CHANNEL_INFO, channelKey, false);

		LoanTransaction transaction = loanService.makeLoanRepayment(accountId, repaymentAmount, date,
				transactionDetails, transactionCustomFields, notes);

		System.out.println("Repaid loan account with the " + accountId + " id response="
				+ transaction.getTransactionId() + "   for amount=" + transaction.getAmount());
	}

	// Test Applying Fee. For Arbitrary Fees available since 3.6. For Manual Predefined fees available since Mambu 4.1
	public static void testApplyFeeToLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testApplyFeeToLoanAccount");

		String accountId = NEW_LOAN_ACCOUNT_ID;
		LoanProductType productType = demoProduct.getLoanProductType();
		// Test predefined fee first. Available since Mambu 4.1
		LoansService loanService = MambuAPIFactory.getLoanService();
		// repayment number parameter is needed only for FIXED_TERM_LOAN and PAYMENT_PLAN products
		boolean needRepaymentNumber = productType == LoanProductType.FIXED_TERM_LOAN
				|| productType == LoanProductType.PAYMENT_PLAN;
		Integer repaymentNumber = needRepaymentNumber ? 3 : null;
		String notes = "Fee Notes";

		// Create demo fees to apply. Get Manual fees only
		List<CustomPredefinedFee> productFees = DemoUtil.makeDemoPredefinedFees(demoProduct,
				new HashSet<>(Collections.singletonList(FeeCategory.MANUAL)));
		if (productFees.size() > 0) {
			// Submit random predefined fee from the list of fees available for this product
			int randomIndex = (int) Math.random() * (productFees.size() - 1);
			CustomPredefinedFee predefinedFee = productFees.get(randomIndex);
			System.out.println("Applying Predefined Fee =" + predefinedFee.getPredefinedFeeEncodedKey() + " Amount="
					+ predefinedFee.getAmount());
			List<CustomPredefinedFee> customFees = new ArrayList<>();
			customFees.add(predefinedFee);
			// Submit API request
			LoanTransaction transaction = loanService.applyFeeToLoanAccount(accountId, customFees, repaymentNumber,
					notes);
			System.out.println("Predefined Fee. TransactionID=" + transaction.getTransactionId() + "\tAmount="
					+ transaction.getAmount().toString() + "\tFees Amount=" + transaction.getFeesAmount());
		} else {
			System.out.println("WARNING: No Predefined Fees defined for product " + demoProduct.getId());
		}

		// Test Arbitrary Fee
		if (demoProduct.getAllowArbitraryFees()) {
			BigDecimal amount = new BigDecimal(15);
			// Use Arbitrary fee API
			String repaymentNumberString = repaymentNumber != null ? repaymentNumber.toString() : null;
			System.out.println("Applying Arbitrary Fee. Amount=" + amount + "\tRepayment Number="
					+ repaymentNumberString);
			// Submit API request
			LoanTransaction transaction = loanService.applyFeeToLoanAccount(accountId, amount.toPlainString(),
					repaymentNumberString, notes);
			System.out.println("Arbitrary Fee. TransactionID=" + transaction.getTransactionId() + "\tAmount="
					+ transaction.getAmount().toString() + "\tFees Amount=" + transaction.getFeesAmount());

		} else {
			System.out.println("WARNING: Arbitrary Fees no allowed for product " + demoProduct.getId());
		}

	}

	public static void testApplyInterestToLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testApplyInterestToLoanAccount");

		LoansService loanService = MambuAPIFactory.getLoanService();

		String accountId = demoLoanAccount.getId();
		System.out.println("For Loan ID=" + accountId);
		Date date = new Date();
		String notes = "Notes for applying interest to a loan";
		try {
			LoanTransaction transaction = loanService.applyInterestToLoanAccount(accountId, date, notes);
			System.out.println("Transaction. ID= " + transaction.getTransactionId().toString()
					+ "\tTransaction Amount=" + transaction.getAmount().toString());
		} catch (MambuApiException e) {
			DemoUtil.logException(methodName, e);
		}
	}

	// Test Loan Actions: Reschedule and Refinance.
	public static void testRescheduleAndRefinanceLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testRescheduleAndRefinanceLoanAccount");

		try {
			LoansService loanService = MambuAPIFactory.getLoanService();

			// Get new copy of the account as we will be updating it's values
			LoanAccount loanAccount = loanService.getLoanAccountDetails(NEW_LOAN_ACCOUNT_ID);
			// Get the ID of the account to refinance/reschedule
			String accountId = loanAccount.getEncodedKey();
			// Clear deprecated fields
			loanAccount.setFirstRepaymentDate(null);
			loanAccount.setExpectedDisbursementDate(null);
			LoanProduct product = loanService.getLoanProduct(loanAccount.getProductTypeKey());
			// Make the new firstRepayDate
			Date firstRepayDate = makeFirstRepaymentDate(loanAccount, product, true);
			loanAccount.setFirstRepaymentDateInDisbursementDetails(firstRepayDate);

			// Clear ID field. Loan details are for the new loan account
			loanAccount.setId(null);
			loanAccount.setInterestRate(loanAccount.getInterestRate());
			// Reschedule demo account first
			BigDecimal principalWriteOff = new BigDecimal(200.00f);
			BigDecimal principalBalance = loanAccount.getPrincipalBalance().getAmount();

			BigDecimal newPrincipalBalance = principalBalance.subtract(principalWriteOff);
			System.out.println("Reschedule: original Principal balance=" + principalBalance + "\tNew="
					+ newPrincipalBalance);
			if (newPrincipalBalance.signum() != 1) {
				newPrincipalBalance = principalBalance;
				principalWriteOff = BigDecimal.ZERO;
				System.out.println("New now=" + newPrincipalBalance);
			}
			RestructureDetails rescheduleDetails = new RestructureDetails();
			rescheduleDetails.setPrincipalWriteOff(principalWriteOff);
			// Set new loan amount
			loanAccount.setLoanAmount(principalBalance.subtract(principalWriteOff));

			JSONRestructureEntity rescheduleEntity = new JSONRestructureEntity();
			rescheduleEntity.setAction(APIData.RESCHEDULE);
			rescheduleEntity.setLoanAccount(loanAccount);
			rescheduleEntity.setRestructureDetails(rescheduleDetails);

			// POST RESCHEDULE action
			System.out.println("Rescheduling account=" + accountId);
			LoanAccount rescheduledAccount = loanService.postLoanAccountRestructureAction(accountId, rescheduleEntity);
			System.out.println("RESCHEDULED OK. New Amount=" + rescheduledAccount.getLoanAmount() + "  Notes="
					+ rescheduledAccount.getNotes());

			// Test Refinancing this New Loan Now
			// REVOLVING_CREDIT accounts cannot be refinanced. See MBU-12052 and MBU-12568
			if (demoProduct.getLoanProductType() != LoanProductType.REVOLVING_CREDIT) {

				accountId = rescheduledAccount.getEncodedKey();
				rescheduledAccount.setFirstRepaymentDate(null);
				rescheduledAccount.setExpectedDisbursementDate(null);
				BigDecimal topUpAmount = new BigDecimal(1000.00f);
				// Set new loan amount, leave other fields as in the original loan account

				BigDecimal loamAmount = rescheduledAccount.getPrincipalBalance().getAmount();
				rescheduledAccount.setLoanAmount(loamAmount.add(topUpAmount));
				rescheduledAccount.setId(null);

				// Create RestructureDetails
				RestructureDetails refinanceDetails = new RestructureDetails();
				refinanceDetails.setTopUpAmount(topUpAmount);

				// /// Refinance API test
				JSONRestructureEntity refinanceEntity = new JSONRestructureEntity();
				refinanceEntity.setAction(APIData.REFINANCE);
				refinanceEntity.setLoanAccount(rescheduledAccount);
				refinanceEntity.setRestructureDetails(refinanceDetails);
				// POST action
				System.out.println("Refinancing account=" + accountId);
				LoanAccount refinancedAccount = loanService
						.postLoanAccountRestructureAction(accountId, refinanceEntity);
				System.out.println("Account Refinanced. New Amount=" + refinancedAccount.getLoanAmount() + "  Notes="
						+ refinancedAccount.getNotes());
			} else {
				System.out.println("WARNING: REVOLVING_CREDIT accounts cannot be refinanced");
			}

		} catch (MambuApiException e) {
			DemoUtil.logException(methodName, e);
		}

	}

	public static void testGetLoanAccountsByBranchCentreOfficerState() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanAccountsByBranchCentreOfficerState");

		LoansService loanService = MambuAPIFactory.getLoanService();

		String branchId = demoClient.getAssignedBranchKey();
		String centreId = demoClient.getAssignedCentreKey(); // Centre ID filter is available since 3.7
		String creditOfficerUserName = demoUser.getUsername();
		String accountState = null; // AccountState.ACTIVE.name(); // CLOSED_WITHDRAWN ACTIVE_IN_ARREARS
		String offset = null;
		String limit = null;

		List<LoanAccount> loanAccounts = loanService.getLoanAccountsByBranchCentreOfficerState(branchId, centreId,
				creditOfficerUserName, accountState, offset, limit);

		if (loanAccounts != null)
			System.out.println("Got loan accounts for the branch, centre, officer, state, total loans="
					+ loanAccounts.size());
		for (LoanAccount account : loanAccounts) {
			System.out.println("Account Name=" + account.getId() + "-" + account.getLoanName() + "\tBranchId="
					+ account.getAssignedBranchKey() + "\tCentreId=" + account.getAssignedCentreKey()
					+ "\tCredit Officer=" + account.getAssignedUserKey());
		}
	}

	public static void testGetLoanAccountsForClient() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanAccountsForClient");
		LoansService loanService = MambuAPIFactory.getLoanService();
		String clientId = demoClient.getId();
		List<LoanAccount> loanAccounts = loanService.getLoanAccountsForClient(clientId);

		System.out.println("Got loan accounts for the client with the " + clientId + " id, Total="
				+ loanAccounts.size());
		for (LoanAccount account : loanAccounts) {
			System.out.println(account.getLoanName() + " - " + account.getId());
		}
		System.out.println();
	}

	public static void testGetLoanAccountsForGroup() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanAccountsForGroup");
		LoansService loanService = MambuAPIFactory.getLoanService();
		String groupId = demoGroup.getId();
		List<LoanAccount> loanAccounts = loanService.getLoanAccountsForGroup(groupId);

		System.out.println("Got " + loanAccounts.size() + " loan accounts for the group with the " + groupId
				+ " id, Total= " + loanAccounts.size());
		int i = 0;
		for (LoanAccount account : loanAccounts) {
			if (i > 0) {
				System.out.print("\n");
			}
			System.out.print(" Account id=" + account.getId() + "\tAccount Name=" + account.getLoanName());
			i++;
		}
		System.out.println();
	}

	// Test request loan account approval - this changes account state from Partial Application to Pending Approval
	public static void testRequestApprovalLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testRequestApprovalLoanAccount");

		// Check if the account is in PARTIAL_APPLICATION state
		if (newAccount == null || newAccount.getAccountState() != AccountState.PARTIAL_APPLICATION) {
			System.out
					.println("WARNING: Need to create loan account in PARTIAL_APPLICATION state to test Request Approval");
			return;
		}
		LoansService loanService = MambuAPIFactory.getLoanService();

		String accountId = newAccount.getId();
		String requestNotes = "Requested Approval by Demo API";
		LoanAccount account = loanService.requestApprovalLoanAccount(accountId, requestNotes);

		System.out.println("Requested Approval for loan account with the " + accountId + " Loan name="
				+ account.getLoanName() + "  Account State=" + account.getState());
	}

	public static void testApproveLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testApproveLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();

		LoanAccount account = loanService.approveLoanAccount(NEW_LOAN_ACCOUNT_ID, "some demo notes");

		System.out.println("Approving loan account with the " + NEW_LOAN_ACCOUNT_ID + " Loan name="
				+ account.getLoanName() + "  Account State=" + account.getState().toString());
	}

	/**
	 * Test Closing Loan account
	 * 
	 * @param closerType
	 *            closer type. Must not be null. Supported closer types are: REJECT, WITHDRAW and CLOSE
	 * @throws MambuApiException
	 */
	public static void testCloseLoanAccount(CLOSER_TYPE closerType) throws MambuApiException {
		System.out.println(methodName = "\nIn testCloseLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();

		// CLose as REJECT or WITHDRAW or CLOSE
		// CLOSER_TYPE.CLOSE or CLOSER_TYPE.REJECT or CLOSER_TYPE.WITHDRAW
		if (closerType == null) {
			throw new IllegalArgumentException("Closer type must not be null");
		}

		LoanAccount account = newAccount; // DemoUtil.getDemoLoanAccount(NEW_LOAN_ACCOUNT_ID);
		String accountId = account.getId();
		String notes = "Closed by Demo Test";

		System.out.println("Closing account with Closer Type=" + closerType + "\tId=" + accountId + "\tState="
				+ account.getAccountState());
		LoanAccount resultAaccount = loanService.closeLoanAccount(accountId, closerType, notes);

		System.out.println("Closed account id:" + resultAaccount.getId() + "\tNew State="
				+ resultAaccount.getAccountState().name() + "\tSubState=" + resultAaccount.getAccountSubState());
	}

	public static void testUndoApproveLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testUndoApproveLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();

		final String accountId = NEW_LOAN_ACCOUNT_ID;
		LoanAccount account = loanService.undoApproveLoanAccount(accountId, "some undo approve demo notes");

		System.out.println("Undo Approving loan account with the " + accountId + " Loan name " + account.getLoanName()
				+ "  Account State=" + account.getState().toString());
	}

	public static void testLockLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testLockLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();

		String accountId = NEW_LOAN_ACCOUNT_ID;
		// Updated to return a list. See MBU-8370
		List<LoanTransaction> transactions = loanService.lockLoanAccount(accountId, "some lock demo notes");

		if (transactions == null || transactions.size() == 0) {
			System.out.println("No Transactions returned in response");
			return;
		}
		for (LoanTransaction transaction : transactions) {
			System.out.println("Locked account with ID " + accountId + " Transaction  "
					+ transaction.getTransactionId() + " Type=" + transaction.getType() + "  Balance="
					+ transaction.getBalance());

		}
	}

	public static void testUnlockLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testUnlockLoanAccount");
		LoansService loanService = MambuAPIFactory.getLoanService();

		String accountId = NEW_LOAN_ACCOUNT_ID;
		// Updated to return a list. See MBU-8370
		List<LoanTransaction> transactions = loanService.unlockLoanAccount(accountId, "some unlock demo notes");
		if (transactions == null || transactions.size() == 0) {
			System.out.println("No Transactions returned in response");
			return;
		}
		for (LoanTransaction transaction : transactions) {
			System.out.println("UnLocked account with ID " + accountId + " Transaction  "
					+ transaction.getTransactionId() + " Type=" + transaction.getType() + "  Balance="
					+ transaction.getBalance());
		}

	}

	public static void testDeleteLoanAccount() throws MambuApiException {
		System.out.println(methodName = "\nIn testDeleteLoanAccount");

		LoansService loanService = MambuAPIFactory.getLoanService();
		String loanAccountId = NEW_LOAN_ACCOUNT_ID;

		boolean status = loanService.deleteLoanAccount(loanAccountId);

		System.out.println("Deletion status=" + status);
	}

	// Loan Products
	public static List<LoanProduct> testGetLoanProducts() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanProducts");
		LoansService loanService = MambuAPIFactory.getLoanService();

		String offset = "0";
		String limit = "100";

		List<LoanProduct> products = loanService.getLoanProducts(offset, limit);

		System.out.println("Got loan products, count=" + products.size());

		if (products.size() > 0) {
			for (LoanProduct product : products) {
				System.out.println("Product=" + product.getName() + "\tId=" + product.getId() + "\tKey="
						+ product.getEncodedKey() + "\tProduct Type=" + product.getLoanProductType());
			}
		}

		return products;

	}

	public static void testGetLoanProductById() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanProductById");
		LoansService loanService = MambuAPIFactory.getLoanService();

		String productId = demoProduct.getId();

		LoanProduct product = loanService.getLoanProduct(productId);
		List<GLAccountingRule> accountingRules = product.getGlProductRules();
		// Log also the count for accounting rules. Available since 3.14, See MBU-10422
		int totalAccountingRules = accountingRules == null ? 0 : accountingRules.size();
		System.out.println("Product=" + product.getName() + "\tId=" + product.getId() + "\tProduct Type="
				+ product.getLoanProductType() + "\tAccountingRules=" + totalAccountingRules);

	}

	public static void testGetLoanProductSchedule() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetLoanProductSchedule");

		if (demoProduct.getLoanProductType() == LoanProductType.REVOLVING_CREDIT) {
			System.out.println("WARNING: Schedule preview is not supported for REVOLVING_CREDIT products");
			return;
		}
		LoansService loanService = MambuAPIFactory.getLoanService();

		String productId = demoProduct.getId();

		LoanAccount loanAccount = makeLoanAccountForDemoProduct();

		// First repayment date is sent in the GET Product Schedule API as a parameter in "yyyy-MM-dd" format
		// The Gson will format this using local time zone, sending incorrect data to Mambu for non UTC time zones
		// Need to adjust it for local time zone
		Date firstRepaymentDate = loanAccount.getFirstRepaymentDateFromDisbursementDetails();
		if (firstRepaymentDate != null) {
			long firstRepaymentTime = firstRepaymentDate.getTime();
			firstRepaymentDate = new Date(firstRepaymentTime - TimeZone.getDefault().getOffset(firstRepaymentTime));
			loanAccount.setFirstRepaymentDateInDisbursementDetails(firstRepaymentDate);
		}
		// The same for expected disbursement date parameter
		Date expectedDisbursementDate = loanAccount.getExpectedDisbursementDateFromDisbursementDetails();
		if (expectedDisbursementDate != null) {
			long expectedDisbursemenTime = expectedDisbursementDate.getTime();
			expectedDisbursementDate = new Date(expectedDisbursemenTime
					- TimeZone.getDefault().getOffset(expectedDisbursemenTime));
			loanAccount.setExpectedDisbursementDateInDisbursementDetails(expectedDisbursementDate);
		}
		System.out.println("Getting schedule with adjusted firstRepaymentDate="
				+ loanAccount.getFirstRepaymentDateFromDisbursementDetails() + "\texpectedDisbursementDate="
				+ loanAccount.getExpectedDisbursementDateFromDisbursementDetails());
		// Get the repayment schedule for these loan params
		List<Repayment> repayments = loanService.getLoanProductSchedule(productId, loanAccount);

		// Log the results
		int totalRepayments = (repayments == null) ? 0 : repayments.size();
		System.out.println("Total repayments=" + totalRepayments + "\tfor product ID=" + productId);
		if (totalRepayments == 0) {
			return;
		}
		Repayment firstRepayment = repayments.get(0);
		Repayment lastRepayment = repayments.get(totalRepayments - 1);
		System.out.println("First Repayment. Due Date=" + firstRepayment.getDueDate() + "\tTotal Due="
				+ firstRepayment.getTotalDue());
		System.out.println("Last Repayment. Due Date=" + lastRepayment.getDueDate() + "\tTotal Due="
				+ lastRepayment.getTotalDue());
	}

	private static final String apiTestIdPrefix = "API-";

	// Create demo loan account with parameters consistent with the demo product
	private static LoanAccount makeLoanAccountForDemoProduct() throws MambuApiException {
		System.out.println(methodName = "\nIn makeLoanAccountForDemoProduct");
		System.out.println("\nProduct name=" + demoProduct.getName() + " id=" + demoProduct.getId());

		if (!demoProduct.isActivated()) {
			System.out.println("*** WARNING ***: demo product is NOT Active. Product name=" + demoProduct.getName()
					+ " id=" + demoProduct.getId());
		}
		LoanAccount loanAccount = new LoanAccount();
		LoanProductType productType = demoProduct.getLoanProductType();
		// Set params to be consistent with the demo product (to be accepted by the GET product schedule API and create
		// loan
		final long time = new Date().getTime();
		loanAccount.setId(apiTestIdPrefix + time); // specifying ID is supported in 3.14
		loanAccount.setLoanName(demoProduct.getName());
		loanAccount.setProductTypeKey(demoProduct.getEncodedKey());

		boolean isForClient = demoProduct.isForIndividuals();
		String holderKey = (isForClient) ? demoClient.getEncodedKey() : demoGroup.getEncodedKey();
		AccountHolderType holderType = (isForClient) ? AccountHolderType.CLIENT : AccountHolderType.GROUP;
		loanAccount.setAccountHolderKey(holderKey);
		loanAccount.setAccountHolderType(holderType);

		// LoanAmount
		Money amountDef = demoProduct.getDefaultLoanAmount();
		Money amountMin = demoProduct.getMinLoanAmount();
		Money amountMax = demoProduct.getMaxLoanAmount();
		Money amount = amountDef;
		amount = amount == null && amountMin != null ? amountMin : amount;
		amount = amount == null && amountMax != null ? amountMax : amount;
		if (amount == null) {
			// Is still null, so no limits
			amount = new Money(3000.00f);
		}
		loanAccount.setLoanAmount(amount); // Mandatory

		// Add periodic payment: required and is mandatory for BALLOON_PAYMENTS products
		Money nullMoney = null;
		loanAccount.setPeriodicPayment(nullMoney);
		if (demoProduct.getAmortizationMethod() == AmortizationMethod.BALLOON_PAYMENTS) {
			loanAccount.setPeriodicPayment(amount.multiply(new BigDecimal(0.5)));
		}
		// InterestRate
		loanAccount.setInterestRate(null);
		loanAccount.setInterestRateSource(null);
		if (demoProduct.getRepaymentScheduleMethod() != RepaymentScheduleMethod.NONE) {
			InterestProductSettings intRateSettings = demoProduct.getInterestRateSettings();
			InterestRateSource rateSource = intRateSettings == null ? null : intRateSettings.getInterestRateSource();

			BigDecimal interestRateDef = intRateSettings == null ? null : intRateSettings.getDefaultInterestRate();
			BigDecimal interestRateMin = intRateSettings == null ? null : intRateSettings.getMinInterestRate();
			BigDecimal interestRateMax = intRateSettings == null ? null : intRateSettings.getMaxInterestRate();

			BigDecimal interestRate = interestRateDef;
			interestRate = interestRate == null && interestRateMin != null ? interestRateMin : interestRate;
			interestRate = interestRate == null && interestRateMax != null ? interestRateMax : interestRate;
			if (interestRate == null) {
				// Is still null, so no limits
				interestRate = new BigDecimal(6.5f);
			}
			if (rateSource == InterestRateSource.INDEX_INTEREST_RATE) {
				loanAccount.setInterestSpread(interestRate); // set the spread
			} else {
				loanAccount.setInterestRate(interestRate); // set the rate
			}
		}

		// Set PrincipalPaymentSettings from product: needed for Revolving Credit products since 3.14
		// See also MBU-12143 - specify Principal Payment for Revolving Credit loans
		if (productType == LoanProductType.REVOLVING_CREDIT) {
			PrincipalPaymentProductSettings productPaymentSettings = demoProduct.getPrincipalPaymentSettings();

			// Set account settings to match product requirements
			PrincipalPaymentAccountSettings principlaAccountSettings = new PrincipalPaymentAccountSettings();
			principlaAccountSettings.setPrincipalPaymentMethod(productPaymentSettings.getPrincipalPaymentMethod());
			// Set also Floor and Ceiling. Otherwise Mambu rejects Create API for OUTSTANDING_PRINCIPAL_PERCENTAGE
			principlaAccountSettings.setPrincipalFloorValue(productPaymentSettings.getPrincipalFloorValue());
			principlaAccountSettings.setPrincipalCeilingValue(productPaymentSettings.getPrincipalCeilingValue());

			PrincipalPaymentMethod method = productPaymentSettings.getPrincipalPaymentMethod();
			switch (method) {
			case FLAT:
				Money defAmount = productPaymentSettings.getDefaultAmount();
				Money minAmount = productPaymentSettings.getMinAmount();
				Money maxAmount = productPaymentSettings.getMaxAmount();
				Money principalAmount = defAmount != null ? defAmount : minAmount;
				principalAmount = principalAmount != null ? principalAmount : maxAmount;
				principalAmount = principalAmount != null ? principalAmount : new Money(100);
				principlaAccountSettings.setAmount(principalAmount);
				break;
			case OUTSTANDING_PRINCIPAL_PERCENTAGE:
				BigDecimal defPercent = productPaymentSettings.getDefaultPercentage();
				BigDecimal minPercent = productPaymentSettings.getMinPercentage();
				BigDecimal maxPercent = productPaymentSettings.getMaxPercentage();
				BigDecimal principalPercent = defPercent != null ? defPercent : minPercent;
				principalPercent = principalPercent != null ? principalPercent : maxPercent;
				principalPercent = principalPercent != null ? principalPercent : new BigDecimal(2);
				principlaAccountSettings.setPercentage(principalPercent);
				break;
			}
			// Set account settings
			loanAccount.setPrincipalPaymentSettings(principlaAccountSettings);
		}

		// DisbursementDate
		// Set dates 3-4 days into the future
		Date now = DemoUtil.getAsMidnightUTC();
		long aDay = 24 * 60 * 60 * 1000L; // 1 day in msecs
		loanAccount.setExpectedDisbursementDateInDisbursementDetails(new Date(now.getTime() + 3 * aDay));
		// Tranches;
		if (productType == LoanProductType.TRANCHED_LOAN) {
			LoanTranche tranche = new LoanTranche(loanAccount.getLoanAmount(), now);
			loanAccount.setExpectedDisbursementDateInDisbursementDetails(null);
			ArrayList<LoanTranche> tanches = new ArrayList<LoanTranche>();
			tranche.setIndex(null); // index must by null. Default is zero
			tanches.add(tranche);
			loanAccount.setTranches(tanches);
		}

		// Since 3.14, Fixed Days can be set at the account level, overwriting product settings. See MBU-10205
		// Set RepaymentPeriodCount and RepaymentPeriodUnit and FixedDays
		ScheduleDueDatesMethod scheduleDueDatesMethod = demoProduct.getScheduleDueDatesMethod();
		// Set all to null and then set only the applicable params
		loanAccount.setRepaymentPeriodCount(null);
		loanAccount.setRepaymentPeriodUnit(null);
		loanAccount.setFixedDaysOfMonth(null);
		switch (scheduleDueDatesMethod) {
		case FIXED_DAYS_OF_MONTH:
			// Set fixed days
			List<Integer> fixedDays = Arrays.asList(2, 18);
			loanAccount.setFixedDaysOfMonth(new ArrayList<>(fixedDays));
			break;
		case INTERVAL:
			// Set RepaymentPeriodCount and RepaymentPeriodUnit
			Integer defRepPeriodCount = demoProduct.getDefaultRepaymentPeriodCount();
			if (defRepPeriodCount == null) {
				defRepPeriodCount = 30;
			}
			loanAccount.setRepaymentPeriodCount(defRepPeriodCount);
			// RepaymentPeriodUnit
			RepaymentPeriodUnit units = demoProduct.getRepaymentPeriodUnit();
			if (units == null) {
				units = RepaymentPeriodUnit.DAYS;
			}
			loanAccount.setRepaymentPeriodUnit(units);
			break;
		}

		// RepaymentInstallments
		Integer repaymentInsatllments = demoProduct.getDefaultNumInstallments();
		repaymentInsatllments = repaymentInsatllments == null ? demoProduct.getMinNumInstallments()
				: repaymentInsatllments;
		repaymentInsatllments = repaymentInsatllments == null ? demoProduct.getMaxNumInstallments()
				: repaymentInsatllments;
		// # of RepaymentInsatllments is not applicable to REVOLVING_CREDIT
		if (repaymentInsatllments == null && productType != LoanProductType.REVOLVING_CREDIT) {
			repaymentInsatllments = 10;
		}
		loanAccount.setRepaymentInstallments(repaymentInsatllments);
		// PrincipalRepaymentInterval
		loanAccount.setPrincipalRepaymentInterval(1);
		Integer principalRepaymentInterva = demoProduct.getDefaultPrincipalRepaymentInterval();
		if (principalRepaymentInterva != null) {
			loanAccount.setPrincipalRepaymentInterval(principalRepaymentInterva);
		}
		// Penalty Rate
		loanAccount.setPenaltyRate(null);
		if (demoProduct.getLoanPenaltyCalculationMethod() != LoanPenaltyCalculationMethod.NONE) {
			BigDecimal defPenaltyRate = demoProduct.getDefaultPenaltyRate();
			BigDecimal minPenaltyRate = demoProduct.getMinPenaltyRate();
			BigDecimal maxPenaltyRate = demoProduct.getMaxPenaltyRate();
			BigDecimal penaltyRate = defPenaltyRate;
			penaltyRate = (penaltyRate == null && minPenaltyRate != null) ? minPenaltyRate : penaltyRate;
			penaltyRate = (penaltyRate == null && maxPenaltyRate != null) ? maxPenaltyRate : penaltyRate;
			loanAccount.setPenaltyRate(penaltyRate);
		}

		// GracePeriod
		loanAccount.setGracePeriod(null);
		if (demoProduct.getGracePeriodType() != GracePeriodType.NONE) {
			Integer defGrace = demoProduct.getDefaultGracePeriod();
			Integer minGrace = demoProduct.getMinGracePeriod();
			Integer maxGrace = demoProduct.getMaxGracePeriod();
			Integer gracePeriod = defGrace;
			gracePeriod = (gracePeriod == null && minGrace != null) ? minGrace : gracePeriod;
			gracePeriod = (gracePeriod == null && maxGrace != null) ? maxGrace : gracePeriod;
			loanAccount.setGracePeriod(gracePeriod);

		}
		// Set Guarantees. Available for API since 3.9. See MBU-6528
		ArrayList<Guaranty> guarantees = new ArrayList<Guaranty>();
		if (demoProduct.isGuarantorsEnabled()) {
			// GuarantyType.GUARANTOR
			Guaranty guarantySecurity = new Guaranty(GuarantyType.GUARANTOR);
			guarantySecurity.setAmount(loanAccount.getLoanAmount());
			guarantySecurity.setGuarantorKey(demoClient.getEncodedKey());
			guarantySecurity.setGuarantorType(demoClient.getAccountHolderType()); // Mambu now supports guarantor type
			guarantees.add(guarantySecurity);
		}
		if (demoProduct.isCollateralEnabled()) {
			// GuarantyType.ASSET
			Guaranty guarantyAsset = new Guaranty(GuarantyType.ASSET);
			guarantyAsset.setAssetName("Asset Name as a collateral");
			guarantyAsset.setAmount(loanAccount.getLoanAmount());
			guarantees.add(guarantyAsset);

		}
		loanAccount.setGuarantees(guarantees);

		// Add funding
		List<InvestorFund> funds = new ArrayList<>();
		if (demoProduct.isFundingSourceEnabled()) {
			// Get Savings account for an investor
			String savingsAccountKey = null;
			try {
				SavingsService savingsService = MambuAPIFactory.getSavingsService();
				List<SavingsAccount> clientSavings = savingsService.getSavingsAccountsForClient(demoClient.getId());
				SavingsAccount savingsAccount = clientSavings != null && clientSavings.size() > 0 ? clientSavings
						.get(0) : null;
				savingsAccountKey = savingsAccount != null ? savingsAccount.getEncodedKey() : null;
			} catch (MambuApiException e) {

			}
			InvestorFund investor = new InvestorFund();
			investor.setAmount(loanAccount.getLoanAmount());
			investor.setSavingsAccountKey(savingsAccountKey);
			// Mambu Supports both Client and Groups as investors since 4.0. See MBU-11403
			investor.setGuarantorKey(demoClient.getEncodedKey());
			investor.setGuarantorType(demoClient.getAccountHolderType());
			funds.add(investor);
		}
		// loanAccount.setFunds(funds);

		// Set first repayment date
		Date firstRepaymentDate = makeFirstRepaymentDate(loanAccount, demoProduct, false);
		loanAccount.setFirstRepaymentDateInDisbursementDetails(firstRepaymentDate);

		// Create demo Transaction details for this account
		TransactionDetails transactionDetails = DemoUtil.makeDemoTransactionDetails();
		DisbursementDetails disbursementDetails = loanAccount.getDisbursementDetails();
		disbursementDetails.setTransactionDetails(transactionDetails);
		String method = transactionDetails.getTransactionChannel().getEncodedKey();

		disbursementDetails.setCustomFieldValues(DemoUtil.makeForEntityCustomFieldValues(
				CustomFieldType.TRANSACTION_CHANNEL_INFO, method, false));

		// Add demo disbursement fees
		List<CustomPredefinedFee> customFees = DemoUtil.makeDemoPredefinedFees(demoProduct,
				new HashSet<>(Collections.singletonList(FeeCategory.DISBURSEMENT)));
		disbursementDetails.setFees(customFees);
		// Disbursement Details are not available for REVOLVING_CREDIT products
		if (productType == LoanProductType.REVOLVING_CREDIT) {
			loanAccount.setDisbursementDetails(null);
		}

		loanAccount.setNotes("Created by DemoTest on " + new Date());
		return loanAccount;

	}

	public static void testGetDocuments() throws MambuApiException {
		System.out.println(methodName = "\nIn testGetDocuments");

		LoanAccount account = DemoUtil.getDemoLoanAccount();
		String accountId = account.getId();

		Integer offset = 0;
		Integer limit = 5;
		DocumentsService documentsService = MambuAPIFactory.getDocumentsService();
		List<Document> documents = documentsService
				.getDocuments(MambuEntityType.LOAN_ACCOUNT, accountId, offset, limit);

		// Log returned documents using DemoTestDocumentsService helper
		System.out.println("Documents returned for a Loan Account with ID=" + accountId);
		DemoTestDocumentsService.logDocuments(documents);

	}

	// Update Custom Field values for the Loan Account and delete the first available custom field
	public static void testUpdateDeleteCustomFields() throws MambuApiException {
		System.out.println(methodName = "\nIn testUpdateDeleteCustomFields");

		// Delegate tests to new since 3.11 DemoTestCustomFiledValueService
		DemoEntityParams demoEntityParams = new DemoEntityParams(newAccount.getName(), newAccount.getEncodedKey(),
				newAccount.getId(), newAccount.getProductTypeKey());
		DemoTestCustomFiledValueService.testUpdateAdddDeleteEntityCustomFields(MambuEntityType.LOAN_ACCOUNT,
				demoEntityParams);

	}

	// Internal clean up routine. Can be used to delete non-disbursed accounts created by these demo test runs
	public static void deleteTestAPILoanAccounts() throws MambuApiException {
		System.out.println(methodName = "\nIn deleteTestAPILoanAccounts");
		System.out.println("**  Deleting all Test Loan Accounts for Client =" + demoClient.getFullNameWithId() + " **");
		LoansService loanService = MambuAPIFactory.getLoanService();
		// Get demo client accounts
		List<LoanAccount> accounts = loanService.getLoanAccountsForClient(demoClient.getId());
		// Get demo group accounts
		List<LoanAccount> groupAccounts = loanService.getLoanAccountsForGroup(demoGroup.getId());
		accounts.addAll(groupAccounts);

		if (accounts.size() == 0) {
			System.out.println("Nothing to delete for client " + demoClient.getFullNameWithId() + " and Group"
					+ demoGroup.getName() + "-" + demoGroup.getId());
			return;
		}
		for (LoanAccount account : accounts) {
			String name = account.getLoanName();
			String id = account.getId();
			if (id.startsWith(apiTestIdPrefix)) {
				AccountState state = account.getAccountState();

				if (state == AccountState.PARTIAL_APPLICATION || state == AccountState.PENDING_APPROVAL
						|| state == AccountState.APPROVED || state == AccountState.ACTIVE
						|| state == AccountState.ACTIVE_IN_ARREARS) {
					System.out.println("Deleting loan account " + name + " ID=" + id);
					try {
						loanService.undoDisburseLoanAccount(id, "Undo by API deletion");
					} catch (MambuApiException e) {
						System.out.println("Account " + id + " is NOT un-disbursed. Exception=" + e.getMessage());
					}
					try {
						boolean deleted = loanService.deleteLoanAccount(id);
						System.out.println("Account " + id + " DELETED. Status=" + deleted);
					} catch (MambuApiException e) {
						System.out.println("Account " + id + " is NOT deleted. Exception=" + e.getMessage());
						continue;
					}
				}
			}
		}

	}

	/**
	 * Make test first repayment date consistent with product settings. Create first repayment date considering its
	 * ScheduleDueDatesMethod and product restrictions, like a definition for a minimum allowed first repayments date
	 * offset or the fixed days repayments.
	 * 
	 * See MBU-9730 -As a Credit Officer, I want to have an offset applied to the first repayment date
	 * 
	 * @param account
	 *            loan account
	 * @param product
	 *            loan product
	 * @param isLocalMidnight
	 *            true if setting first repayment date to be local midnight. return as UTC midnight date otherwise
	 * @return first repayment date
	 */
	private static Date makeFirstRepaymentDate(LoanAccount account, LoanProduct product, boolean isLocalMidnight) {
		if (account == null || product == null) {
			return null;
		}
		Date disbDate = account.getExpectedDisbursementDateFromDisbursementDetails();
		if (disbDate == null) {
			disbDate = new Date();
		}
		long aDay = 24 * 60 * 60 * 1000L; // 1 day in msecs
		Date firstRepaymentDate = new Date(disbDate.getTime() + 4 * aDay); // default to 4 days from disbursement date

		// Set the first repayment date depending on product's ScheduleDueDatesMethod
		ScheduleDueDatesMethod scheduleDueDatesMethod = product.getScheduleDueDatesMethod();
		System.out.println("ScheduleDueDatesMethod=" + scheduleDueDatesMethod);
		if (scheduleDueDatesMethod == null) {
			return firstRepaymentDate;
		}

		switch (scheduleDueDatesMethod) {
		case FIXED_DAYS_OF_MONTH:
			// Since 3.14 Fixed Days are defined at the account level. See MBU-10205 and MBU-10802
			List<Integer> fixedDays = account.getFixedDaysOfMonth();
			firstRepaymentDate = makeFixedDateFirstRepayment(fixedDays, isLocalMidnight);
			return firstRepaymentDate;
		case INTERVAL:
			// For INTERVAL due dates product check for the allowed minimum offset time
			Integer repaymentPeriodCount = account.getRepaymentPeriodCount();
			RepaymentPeriodUnit unit = account.getRepaymentPeriodUnit();
			if (unit == null || repaymentPeriodCount == null) {
				return null;
			}

			// get minimum offset
			Integer minOffsetDays = product.getMinFirstRepaymentDueDateOffset();
			Integer maxOffsetDays = product.getMaxFirstRepaymentDueDateOffset();
			System.out.println("INTERVAL schedule due dates product. Min offset=" + minOffsetDays
					+ " RepaymentPeriodUnit=" + unit + " repaymentPeriodCount=" + repaymentPeriodCount);

			if (minOffsetDays == null && maxOffsetDays == null) {
				// if no offset to 4 days in a future
				return firstRepaymentDate;
			}
			minOffsetDays = minOffsetDays != null ? minOffsetDays : maxOffsetDays;
			// Create UTC disbursement date with day, month year only
			Calendar disbDateCal = Calendar.getInstance();
			int day = disbDateCal.get(Calendar.DAY_OF_MONTH);
			int year = disbDateCal.get(Calendar.YEAR);
			int month = disbDateCal.get(Calendar.MONTH);
			disbDateCal.clear();
			disbDateCal.setTimeZone(TimeZone.getTimeZone("UTC"));
			disbDateCal.set(year, month, day);

			// Create calendar for firstRepaymDate and set it to be equal to the disbursement date
			Calendar firstRepaymDateCal = Calendar.getInstance();
			firstRepaymDateCal.clear();
			firstRepaymDateCal.setTimeZone(TimeZone.getTimeZone("UTC"));
			firstRepaymDateCal.setTime(disbDateCal.getTime());

			// Calculate the first expected repayment date without the offset
			switch (unit) {
			case DAYS:
				firstRepaymDateCal.add(Calendar.DAY_OF_MONTH, repaymentPeriodCount);
				break;
			case MONTHS:
				firstRepaymDateCal.add(Calendar.MONTH, repaymentPeriodCount);
				break;
			case WEEKS:
				firstRepaymDateCal.add(Calendar.DAY_OF_MONTH, (repaymentPeriodCount * 7));
				break;
			case YEARS:
				firstRepaymDateCal.add(Calendar.YEAR, repaymentPeriodCount);
				break;
			}
			// Add minimum offset in days
			firstRepaymDateCal.add(Calendar.DAY_OF_MONTH, 1 + minOffsetDays);
			// Get firstRepaymentDate
			firstRepaymentDate = firstRepaymDateCal.getTime();
			return firstRepaymentDate;
		}

		System.out.println("FirstRepaymentDate =" + firstRepaymentDate);
		return firstRepaymentDate;

	}

	private static Date makeFixedDateFirstRepayment(List<Integer> fixedDays, boolean isLocalMidnight) {
		Date firstRepaymentDate = null;
		System.out.println("Fixed day product:" + fixedDays);
		if (fixedDays != null && fixedDays.size() > 0) {
			Calendar date = Calendar.getInstance();
			int year = date.get(Calendar.YEAR);
			int month = date.get(Calendar.MONTH);
			date.clear();
			date.setTimeZone(TimeZone.getTimeZone("UTC"));
			int fixedDay = fixedDays.get(fixedDays.size() - 1);
			date.set(year, month + 1, fixedDay);
			firstRepaymentDate = date.getTime();
		}
		// When sent in "yyyy-MM-dd" format, the GMT midnight date will be formatted into using local time zone.
		// Need to preserve the day, especially for fixed day products
		if (isLocalMidnight) {
			firstRepaymentDate = new Date(firstRepaymentDate.getTime()
					- TimeZone.getDefault().getOffset(firstRepaymentDate.getTime()));
		}
		return firstRepaymentDate;
	}

	// Log Loan Disbursement Details. Available since 4.0. See MBU-11223 and MBU-11800
	private static void logDisbursementDetails(DisbursementDetails disbDetails) {
		if (disbDetails == null) {
			return;
		}
		System.out.println("DisbursementDetails:\tFirstRepaymentDate=" + disbDetails.getFirstRepaymentDate()
				+ "\tDisbursementDate:" + disbDetails.getDisbursementDate() + "\tEntityName:"
				+ disbDetails.getEntityName() + "\tEntityType:" + disbDetails.getEntityType());

		// Log TransactionDetails
		TransactionDetails transactionDetails = disbDetails.getTransactionDetails();
		String channelId = null;
		String channelName = null;
		if (transactionDetails != null) {
			TransactionChannel transactionChannel = transactionDetails.getTransactionChannel();
			channelId = transactionChannel != null ? transactionChannel.getId() : null;
			channelName = transactionChannel != null ? transactionChannel.getName() : null;
			System.out.println("\tChannel: ID=" + channelId + " Name =" + channelName);
		} else {
			System.out.println("\tNull transaction details");
		}
		// Log Transaction Custom Fields. Available since Mambu 4.1. See MBU-11800
		List<CustomFieldValue> transactionFields = disbDetails.getCustomFieldValues();
		if (transactionFields != null) {
			System.out.println("Total Transaction Fields= " + transactionFields.size());
			DemoUtil.logCustomFieldValues(transactionFields, "Channel", channelId);
		} else {
			System.out.println("\tNull transaction custom fields");
		}

		// Log disbursement Fees
		List<CustomPredefinedFee> dibsursementFees = disbDetails.getFees();
		if (dibsursementFees != null) {
			System.out.println("\nDisbursement Fees=" + dibsursementFees.size());
			for (CustomPredefinedFee customFee : dibsursementFees) {
				System.out.println("\tAmount=" + customFee.getAmount());
				PredefinedFee fee = customFee.getFee();
				if (fee == null) {
					continue;
				}
				System.out.println("\tPredefinedFee=" + fee.getEncodedKey() + "\tAmount=" + fee.getAmount());
			}
		}
	}
}
