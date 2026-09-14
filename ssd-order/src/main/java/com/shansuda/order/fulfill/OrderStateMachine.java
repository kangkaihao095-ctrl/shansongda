package com.shansuda.order.fulfill;

import com.shansuda.common.api.BizException;
import com.shansuda.common.api.ErrorCodes;

public final class OrderStateMachine {

    private OrderStateMachine() {
    }

    public static String pay(String from) {
        require(from, "CREATED");
        return "MERCHANT_PENDING";
    }

    public static String merchantAccept(String from) {
        require(from, "MERCHANT_PENDING");
        return "PAID";
    }

    public static String accept(String from) {
        require(from, "PAID");
        return "ACCEPTED";
    }

    public static String arrive(String from) {
        require(from, "ACCEPTED");
        return "ARRIVED";
    }

    public static String deliver(String from) {
        if ("ARRIVED".equals(from) || "ACCEPTED".equals(from)) {
            return "DELIVERING";
        }
        throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "不能从 " + from + " 开始配送，期望 ACCEPTED 或 ARRIVED");
    }

    public static String complete(String from) {
        require(from, "DELIVERING");
        return "COMPLETED";
    }

    public static String applyCancel(String from) {
        if ("CREATED".equals(from)) {
            return "CANCELLING";
        }
        throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "当前状态请走退款申请: " + from);
    }

    public static String confirmCancel(String from) {
        require(from, "CANCELLING");
        return "CANCELLED";
    }

    public static String applyRefund(String from) {
        if ("MERCHANT_PENDING".equals(from) || "PAID".equals(from) || "ACCEPTED".equals(from)
                || "ARRIVED".equals(from) || "DELIVERING".equals(from) || "COMPLETED".equals(from)
                || "REFUND_REJECTED".equals(from)) {
            return "REFUNDING";
        }
        throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "当前状态不可申请退款: " + from);
    }

    public static String approveRefund(String from) {
        require(from, "REFUNDING");
        return "REFUNDED";
    }

    public static String rejectRefund(String from) {
        require(from, "REFUNDING");
        return "REFUND_REJECTED";
    }

    /** 驳回已写入 REFUND_REJECTED 后，迁回申请前的 resume_status 继续履约。 */
    public static String resumeAfterReject(String from, String resumeStatus) {
        require(from, "REFUND_REJECTED");
        if (resumeStatus == null || resumeStatus.isBlank()) {
            return "PAID";
        }
        if ("REFUNDING".equals(resumeStatus) || "REFUNDED".equals(resumeStatus)
                || "REFUND_REJECTED".equals(resumeStatus) || "CANCELLED".equals(resumeStatus)
                || "CANCELLING".equals(resumeStatus)) {
            throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "不可恢复到状态: " + resumeStatus);
        }
        return resumeStatus;
    }

    /** 兼容旧口径：未支付取消一步到位；已支付必须先申请退款。 */
    public static String cancel(String from) {
        if ("CREATED".equals(from) || "CANCELLING".equals(from)) {
            return "CANCELLED";
        }
        if ("DELIVERING".equals(from) || "COMPLETED".equals(from) || "CANCELLED".equals(from)
                || "REFUNDED".equals(from)) {
            throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "当前状态不可取消: " + from);
        }
        throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "已支付订单请申请退款，当前状态: " + from);
    }

    private static void require(String from, String expected) {
        if (!expected.equals(from)) {
            throw BizException.conflict(ErrorCodes.ILLEGAL_STATE, "不能从 " + from + " 迁到下一状态，期望 " + expected);
        }
    }
}
