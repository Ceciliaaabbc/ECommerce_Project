UPDATE orders
SET status = 'PENDING_PAYMENT'
WHERE status = 'PENDING';

UPDATE orders
SET payment_status = 'UNPAID'
WHERE payment_status IS NULL
  AND status IN ('PENDING_PAYMENT', 'PENDING');

UPDATE orders
SET payment_status = 'PAID'
WHERE payment_status IS NULL
  AND status = 'PAID';

UPDATE orders
SET payment_status = 'CANCELLED'
WHERE status = 'CANCELLED'
  AND (payment_status IS NULL OR payment_status NOT IN ('PAID', 'EXPIRED', 'REFUNDED'));

UPDATE orders
SET status = 'CANCELLED'
WHERE payment_status IN ('CANCELLED', 'EXPIRED')
  AND status NOT IN ('CANCELLED', 'REFUNDED');

UPDATE orders
SET status = 'REFUNDED'
WHERE payment_status = 'REFUNDED';

UPDATE orders
SET status = 'PENDING_PAYMENT'
WHERE status IS NULL;

UPDATE orders
SET payment_status = 'UNPAID'
WHERE payment_status IS NULL;
