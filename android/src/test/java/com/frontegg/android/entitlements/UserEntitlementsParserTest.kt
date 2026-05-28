package com.frontegg.android.entitlements

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser fidelity tests — the JSON-to-structure shape needs to match exactly what
 * `@frontegg/entitlements-javascript-commons` consumes, otherwise the evaluators get
 * the wrong inputs.
 */
class UserEntitlementsParserTest {

    private fun parse(json: String): UserEntitlementsContext =
        UserEntitlementsParser.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `FR_24821 minimal response — sso feature linked to a defaultTreatment=false plan`() {
        // The shape Yonatan posted in Slack:
        //   features.sso = { planIds: ["ID_1"], expireTime: null }
        //   plans.ID_1 = { defaultTreatment: "false" }
        val json = """
            {
              "features": {
                "sso": { "planIds": ["ID_1"], "expireTime": null, "linkedPermissions": [] }
              },
              "plans": {
                "ID_1": { "defaultTreatment": "false" }
              },
              "permissions": {}
            }
        """.trimIndent()
        val ctx = parse(json)
        val sso = ctx.features["sso"]
        assertNotNull("sso feature must parse", sso)
        assertEquals(listOf("ID_1"), sso!!.planIds)
        assertNull("expireTime explicitly null must remain null", sso.expireTime)

        val plan = ctx.plans["ID_1"]
        assertNotNull("plan ID_1 must parse", plan)
        assertEquals(Treatment.FALSE, plan!!.defaultTreatment)
    }

    @Test
    fun `expireTime as NO_EXPIRATION_TIME parses correctly`() {
        val json = """{"features":{"alpha":{"planIds":[],"expireTime":-1,"linkedPermissions":[]}},"plans":{},"permissions":{}}"""
        val ctx = parse(json)
        assertEquals(FeatureDetail.NO_EXPIRATION_TIME, ctx.features["alpha"]!!.expireTime)
    }

    @Test
    fun `permissions map drops non-boolean and false entries`() {
        val json = """
            {
              "features": {},
              "plans": {},
              "permissions": {
                "fe.read": true,
                "fe.write": false,
                "fe.junk": "not-a-bool"
              }
            }
        """.trimIndent()
        val ctx = parse(json)
        assertEquals(true, ctx.permissions["fe.read"])
        assertEquals(false, ctx.permissions["fe.write"])
        assertTrue("non-boolean permission entry must not be retained", "fe.junk" !in ctx.permissions)
    }

    @Test
    fun `rule with unknown operation is dropped from its parent rule list`() {
        // Forward-compat: the server may add operations the SDK doesn't know yet.
        // The whole condition (and therefore the rule, since it has zero valid
        // conditions left) drops out — better than crashing the parse.
        val json = """
            {
              "features": {
                "alpha": {
                  "planIds": ["p1"], "expireTime": null, "linkedPermissions": []
                }
              },
              "plans": {
                "p1": {
                  "defaultTreatment": "false",
                  "rules": [
                    {
                      "conditionLogic": "and",
                      "treatment": "true",
                      "conditions": [
                        {"attribute":"x","negate":false,"op":"future_op","value":{}}
                      ]
                    }
                  ]
                }
              },
              "permissions": {}
            }
        """.trimIndent()
        val ctx = parse(json)
        val plan = ctx.plans["p1"]!!
        assertEquals("future-op rule must drop", 0, plan.rules?.size)
    }

    @Test
    fun `feature flag with rules parses end-to-end`() {
        val json = """
            {
              "features": {
                "beta": {
                  "planIds": [],
                  "expireTime": null,
                  "linkedPermissions": [],
                  "featureFlag": {
                    "on": true,
                    "offTreatment": "false",
                    "defaultTreatment": "false",
                    "rules": [
                      {
                        "conditionLogic": "and",
                        "treatment": "true",
                        "conditions": [
                          {"attribute":"frontegg.tenantId","negate":false,"op":"in_list","value":{"list":["tenant-A"]}}
                        ]
                      }
                    ]
                  }
                }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        val ctx = parse(json)
        val flag = ctx.features["beta"]!!.featureFlag!!
        assertEquals(true, flag.on)
        assertEquals(Treatment.FALSE, flag.offTreatment)
        assertEquals(Treatment.FALSE, flag.defaultTreatment)
        val rule = flag.rules!![0]
        assertEquals(Treatment.TRUE, rule.treatment)
        assertEquals("frontegg.tenantId", rule.conditions[0].attribute)
        assertEquals(Operation.IN_LIST, rule.conditions[0].op)
    }

    @Test
    fun `feature flag with missing required field drops the flag, keeps the feature`() {
        val json = """
            {
              "features": {
                "beta": {
                  "planIds": [],
                  "expireTime": null,
                  "linkedPermissions": [],
                  "featureFlag": {"on": true}
                }
              },
              "plans": {},
              "permissions": {}
            }
        """.trimIndent()
        val ctx = parse(json)
        val beta = ctx.features["beta"]
        assertNotNull(beta)
        assertNull("malformed featureFlag must drop, not crash the feature", beta!!.featureFlag)
    }
}
