#!/bin/sh
psql -d gingrsnapdb --command "drop table play_evolutions, ingredient, recipe, account, gingrsnapuser, image, recipeimage, gingrsnapuserimage cascade;"
