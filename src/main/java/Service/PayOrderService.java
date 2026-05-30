package Service;

import Dto.PayOrderDTO;
import Dto.Result;
import org.springframework.stereotype.Service;


public interface PayOrderService {
    Result createPayOrder(PayOrderDTO dto);

    Result invalidOrder(String orderNo);

    Result getWaitPayByPatient(Long patientId);

    Result pay(String orderNo, Long payerId);

    Result myList(Long patientId);
}
