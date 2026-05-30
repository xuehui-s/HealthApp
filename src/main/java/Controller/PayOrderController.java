package Controller;

import Dto.PayOrderDTO;
import Dto.Result;
import Service.PayOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payOrder")
public class PayOrderController {

    // 只注入缴费相关 service
    @Autowired
    private PayOrderService payOrderService;

    // ==================== 医生端 ====================
    // 医生开缴费单
    @PostMapping("/create")
    public Result createPayOrder(@RequestBody PayOrderDTO dto) {
        return payOrderService.createPayOrder(dto);
    }

    // 医生作废缴费单
    @PostMapping("/invalid")
    public Result invalidOrder(@RequestParam String orderNo) {
        return payOrderService.invalidOrder(orderNo);
    }

    // ==================== 收费员端 ====================
    // 收费员查询患者待缴费单
    @GetMapping("/waitPay/{patientId}")
    public Result getWaitPay(@PathVariable Long patientId) {
        return payOrderService.getWaitPayByPatient(patientId);
    }

    // 收费员确认缴费
    @PostMapping("/pay")
    public Result pay(
            @RequestParam String orderNo,
            @RequestParam Long payerId
    ) {
        return payOrderService.pay(orderNo, payerId);
    }

    // ==================== 患者端 ====================
    // 我的缴费单
    @GetMapping("/myList")
    public Result myList(@RequestHeader("patientId") Long patientId) {
        return payOrderService.myList(patientId);
    }
}