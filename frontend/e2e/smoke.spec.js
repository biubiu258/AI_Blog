import { expect, test } from '@playwright/test'

test('登录页可渲染', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '登 录' })).toBeVisible()
  await expect(page.getByRole('button', { name: '登 录' })).toBeVisible()
})

test('未登录访问写作页跳转登录', async ({ page }) => {
  await page.goto('/write')
  await expect(page).toHaveURL(/\/login/)
})

test('热门排行页可渲染', async ({ page }) => {
  await page.goto('/ranking')
  await expect(page.getByText('热门排行')).toBeVisible()
  await expect(page.getByText('本周', { exact: true })).toBeVisible()
})
