<template>
  <div class="sys-params">
    <el-card shadow="never">
      <template #header>
        <div class="bar"><span>系统参数</span></div>
      </template>

      <el-form label-width="180px" @submit.prevent>
        <el-form-item label="横向项目管理费比例(%)">
          <div class="row">
            <el-input-number v-model="rate" :min="0" :max="100" :precision="2" :step="0.5"
              :controls="false" class="w-140" />
            <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存</el-button>
          </div>
          <div class="tip">教师登记选「收取管理费=是」时,金额 = 合同经费 × 该比例自动换算,教师不可修改;
            已提交项目不追改,经费变更通过时按当时比例重算。</div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<!--
  系统参数(admin):sys_config 值型业务参数维护页。首项=横向管理费比例
  (hx_mgmt_fee_rate,教师端只读自动换算的口径源头);后续值型配置挂本页。
-->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig, putConfig } from '../../api/hx'

const rate = ref(null)
const loaded = ref(null)   // 保存基准(判 dirty)
const saving = ref(false)
const dirty = computed(() => rate.value != null && loaded.value != null && rate.value !== loaded.value)

onMounted(async () => {
  const v = await getConfig('hx_mgmt_fee_rate')
  rate.value = v == null ? null : Number(v)
  loaded.value = rate.value
})

async function save() {
  if (rate.value == null || rate.value < 0 || rate.value > 100) {
    ElMessage.warning('比例须在 0~100 之间')
    return
  }
  saving.value = true
  try {
    await putConfig('hx_mgmt_fee_rate', String(rate.value))
    loaded.value = rate.value
    ElMessage.success('管理费比例已保存')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.sys-params {
  max-width: 760px;
}
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.row {
  display: flex;
  gap: 12px;
  align-items: center;
}
.w-140 {
  width: 140px;
}
.tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
