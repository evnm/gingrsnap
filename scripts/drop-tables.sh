#!/bin/sh
psql -d postgres --command "drop table play_evolutions, ingredient, recipe, \
account, gingrsnapuser, image, recipeimage, gingrsnapuserimage, make, event, \
passwordresetrequest, feature, tip, follow, recipelist cascade;"
