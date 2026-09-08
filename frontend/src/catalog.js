export const CATEGORIES = [
  { key: 'food', name: '美食', image: '/images/cats/food.jpg' },
  { key: 'dessert', name: '甜点饮品', image: '/images/cats/dessert.jpg' },
  { key: 'market', name: '超市便利', image: '/images/cats/market.jpg' },
  { key: 'fresh', name: '生鲜果蔬', image: '/images/cats/fresh.jpg' },
  { key: 'pharma', name: '医药健康', image: '/images/cats/pharma.jpg' },
  { key: 'flower', name: '鲜花蛋糕', image: '/images/cats/flower.jpg' },
  { key: 'tea', name: '下午茶', image: '/images/cats/tea.jpg' },
  { key: 'errand', name: '跑腿', image: '/images/cats/errand.jpg' }
]

export function categoryName(key) {
  return CATEGORIES.find((c) => c.key === key)?.name || key
}
