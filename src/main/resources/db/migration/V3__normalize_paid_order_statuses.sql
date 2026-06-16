UPDATE orders
SET status = 'PROCESSING'
WHERE status = 'PAID'
  AND payment_status = 'PAID';
