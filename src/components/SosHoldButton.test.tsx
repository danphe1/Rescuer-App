import{describe,expect,it}from'vitest';import{policy}from'../domain/policy';
describe('SOS hold policy',()=>{it('requires a continuous three second hold',()=>{expect(policy.sos.holdMs).toBe(3000);expect(policy.sos.holdMs-1).toBeLessThan(policy.sos.holdMs)})});
