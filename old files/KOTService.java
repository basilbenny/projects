package com.finatel.whizhop.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finatel.whizhop.domain.Bill;
import com.finatel.whizhop.domain.BillItem;
import com.finatel.whizhop.domain.KOT;
import com.finatel.whizhop.dto.BaseDTO;
import com.finatel.whizhop.dto.BillDTO;
import com.finatel.whizhop.dto.BillItemDTO;
import com.finatel.whizhop.enumeration.CodeDescription;
import com.finatel.whizhop.enumeration.Status;
import com.finatel.whizhop.repository.BillItemRepository;
import com.finatel.whizhop.repository.BillRepository;
import com.finatel.whizhop.repository.KOTRepository;
import com.finatel.whizhop.util.Validate;

import lombok.extern.log4j.Log4j;

@Service
@Log4j
public class KOTService {

	@Autowired
	BillRepository  billRepository;
	
	@Autowired
	KOTRepository kotRepo;

	@Autowired
	BillItemRepository billItemRepo;
	
	/**
	 * Add kot
	 * @param billDto
	 * @return
	 */
	
	@Transactional(rollbackFor = Exception.class)
	public BaseDTO saveKot(BillDTO billDto) {
		
		Bill bill = new Bill(billDto);
		Map<Integer, List<BillItemDTO>> billItemKotMap = new HashMap<Integer, List<BillItemDTO>>();

		billDto.getBillItems().forEach(billItem -> {
			if (billItemKotMap.containsKey(billItem.getKotNo())) {
				List<BillItemDTO> billList = billItemKotMap.get(billItem.getKotNo());
				billList.add(billItem);
				billItemKotMap.put(billItem.getKotNo(), billList);
			} else {
				List<BillItemDTO> billList = new ArrayList<BillItemDTO>();
				billList.add(billItem);
				billItemKotMap.put(billItem.getKotNo(), billList);
			}
		});
		List<KOT> kotList = new ArrayList<KOT>();
		billItemKotMap.forEach((kotNo, billItems) -> {
			KOT kot = new KOT();
			kot.setBill(bill);
			kot.setCreatedDate(new Date());
			kot.setStatus(Status.ORDERED);
			kot.setKotNumber(kotNo);
			
			List<BillItem> billItemList = new ArrayList<BillItem>();

			billItems.forEach(billItem -> {
				Validate.notNull(billItem.getStatus(), CodeDescription.BILL_ITEM_STATUS_REQUIRED);
				Validate.notNull(billItem.getProductId(), CodeDescription.BILL_ITEM_PRODUCTID_REQUIRED);
				Validate.notNull(billItem.getCategoryId(), CodeDescription.BILL_ITEM_CATEGEORYID_REQUIRED);
				Validate.notNull(billItem.getProductName(), CodeDescription.BILL_ITEM_PRODUCT_NAME_REQUIRED);
				Validate.notNull(billItem.getItemQuantity(), CodeDescription.BILL_ITEM_QUANTITY_REQUIRED);
				Validate.notNull(billItem.getUom(), CodeDescription.BILL_ITEM_UOM_REQUIRED);

				BillItem item = new BillItem(billItem);
				item.setKot(kot);
				item.setOrderedTime(new Date());
				billItemList.add(item);
			});
			kot.setItems(billItemList);

			kotList.add(kot);
		});
		bill.setKotList(kotList);
		billRepository.save(bill);
		return new BaseDTO(CodeDescription.KOT_ADD_SUCCESS, true, null);
		
	}
	
	
	/**
	 * cancel kot
	 * @param billDTO
	 * @return
	 */
	@Transactional(rollbackFor = Exception.class)
	public BaseDTO cancelKot(BillDTO billDTO) {
		
		Set<Long> kotIds = new HashSet<Long>();
		List<BillItem> billItems = new ArrayList<BillItem>();
		

		billDTO.getBillItems().forEach(billItem -> {
			BillItem bi = billItemRepo.findByClientId(billItem.getId());
			bi.setStatus(Status.CANCELLED);
			kotIds.add(bi.getKot().getId());
			billItems.add(bi);
		});
		billItemRepo.saveAll(billItems);
		
		kotIds.forEach(kotId -> {
			List<BillItem> billItemList = billItemRepo.findByKotIdAndStatusNotMatching(Status.CANCELLED, kotId);
			if (billItemList == null || billItemList.size() == 0) {
				Optional<KOT> kotEntity = kotRepo.findById(kotId);
				if (kotEntity.isPresent()) {
					log.info("Updating Kot status to CANCELLED");
					KOT kot = kotEntity.get();
					kot.setStatus(Status.CANCELLED);
					kotRepo.save(kot);
				}
			} else {
				boolean isReady = true;
				for (BillItem billItem : billItemList) {
					if (billItem.getStatus().equals(Status.STARTED)
							|| billItem.getStatus().equals(Status.ORDERED)) {
						isReady = false;
						break;
					}
				}
				if (isReady) {
					Optional<KOT> kotEntity = kotRepo.findById(kotId);
					if (kotEntity.isPresent()) {
						log.info("Updating Kot status to READY");
						KOT kot = kotEntity.get();
						kot.setStatus(Status.READY);
						kotRepo.save(kot);
					}
				}
			}
		});
		return new BaseDTO(CodeDescription.KOT_CANCEL_SUCCESS, true, null);
	}
}
