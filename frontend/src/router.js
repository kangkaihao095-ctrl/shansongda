import { createRouter, createWebHistory } from 'vue-router'
import { boot, session } from './session'
import { resolveNavigation } from './guard'
import LoginView from './views/LoginView.vue'
import UserHome from './views/UserHome.vue'
import OrdersView from './views/OrdersView.vue'
import OrderDetailView from './views/OrderDetailView.vue'
import ReviewView from './views/ReviewView.vue'
import RiderHome from './views/RiderHome.vue'
import MerchantHome from './views/MerchantHome.vue'
import ProfileView from './views/ProfileView.vue'
import ShopView from './views/ShopView.vue'
import CategoryView from './views/CategoryView.vue'
import CheckoutView from './views/CheckoutView.vue'
import PayView from './views/PayView.vue'
import ActivityView from './views/ActivityView.vue'
import CouponsView from './views/CouponsView.vue'
import MyReviewsView from './views/MyReviewsView.vue'
import MemberPayView from './views/MemberPayView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', redirect: '/home' },
    { path: '/home', component: UserHome, meta: { role: 'USER' } },
    { path: '/category/:key', component: CategoryView, meta: { role: 'USER' } },
    { path: '/shops/:id', component: ShopView, meta: { role: 'USER' } },
    { path: '/checkout', component: CheckoutView, meta: { role: 'USER' } },
    { path: '/activities/:id', component: ActivityView, meta: { role: 'USER' } },
    { path: '/coupons', component: CouponsView, meta: { role: 'USER' } },
    { path: '/me/reviews', component: MyReviewsView, meta: { role: 'USER' } },
    { path: '/member/pay', component: MemberPayView, meta: { role: 'USER' } },
    { path: '/orders', component: OrdersView },
    { path: '/orders/:id/pay', component: PayView, meta: { role: 'USER' } },
    { path: '/orders/:id/review', component: ReviewView, meta: { role: 'USER' } },
    { path: '/orders/:id', component: OrderDetailView },
    { path: '/rider', component: RiderHome, meta: { role: 'RIDER' } },
    { path: '/shop', component: MerchantHome, meta: { role: 'MERCHANT' } },
    { path: '/me', component: ProfileView }
  ]
})

let booted = false

router.beforeEach(async (to) => {
  if (!booted) {
    await boot()
    booted = true
  }
  return resolveNavigation(to, session.me)
})

export default router
